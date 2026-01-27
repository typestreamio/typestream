import type { Node, Edge } from '@xyflow/react';
import Dagre from '@dagrejs/dagre';
import type { PipelineGraph, PipelineNode } from '../generated/job_pb';

/**
 * Result of deserializing a PipelineGraph
 */
export interface DeserializedGraph {
  nodes: Node[];
  edges: Edge[];
}

/**
 * Node type categories for layout positioning
 */
type NodeCategory = 'source' | 'transform' | 'sink';

/**
 * Get display label for a node based on its type
 */
function getNodeLabel(pipelineNode: PipelineNode): string {
  const nodeType = pipelineNode.nodeType;
  switch (nodeType.case) {
    case 'streamSource':
      return nodeType.value.dataStream?.path ?? 'Source';
    case 'shellSource':
      return 'Shell Source';
    case 'sink':
      return nodeType.value.output?.path ?? 'Sink';
    case 'geoIp':
      return `GeoIP (${nodeType.value.ipField})`;
    case 'inspector':
      return nodeType.value.label || 'Inspector';
    case 'filter':
      return `Filter: ${nodeType.value.predicate?.expr ?? ''}`;
    case 'map':
      return `Map: ${nodeType.value.mapperExpr}`;
    case 'group':
      return `Group: ${nodeType.value.keyMapperExpr}`;
    case 'count':
      return 'Count';
    case 'windowedCount':
      return `Windowed Count (${nodeType.value.windowSizeSeconds}s)`;
    case 'reduceLatest':
      return 'Reduce Latest';
    case 'join':
      return `Join: ${nodeType.value.with?.path ?? ''}`;
    case 'each':
      return `Each: ${nodeType.value.fnExpr}`;
    case 'noop':
      return 'NoOp';
    case 'textExtractor':
      return `Text Extractor (${nodeType.value.filePathField})`;
    case 'embeddingGenerator':
      return `Embedding (${nodeType.value.textField})`;
    case 'openAiTransformer':
      return `AI Transform (${nodeType.value.model})`;
    default:
      return 'Unknown';
  }
}

/**
 * Determine the category of a node for layout purposes
 */
function getNodeCategory(pipelineNode: PipelineNode): NodeCategory {
  const nodeType = pipelineNode.nodeType;
  switch (nodeType.case) {
    case 'streamSource':
    case 'shellSource':
      return 'source';
    case 'sink':
      return 'sink';
    default:
      return 'transform';
  }
}

/**
 * Map proto node type to React Flow node type
 */
function getReactFlowNodeType(pipelineNode: PipelineNode): string {
  const nodeType = pipelineNode.nodeType;
  switch (nodeType.case) {
    case 'streamSource':
      return 'kafkaSource';
    case 'sink':
      return 'kafkaSink';
    case 'geoIp':
      return 'geoIp';
    case 'inspector':
      return 'inspector';
    case 'group':
      return 'materializedView'; // Group is part of materializedView in UI
    case 'count':
    case 'reduceLatest':
      return 'materializedView';
    case 'textExtractor':
      return 'textExtractor';
    case 'embeddingGenerator':
      return 'embeddingGenerator';
    case 'openAiTransformer':
      return 'openAiTransformer';
    default:
      // For nodes that don't have a direct UI mapping, use a generic display
      return 'default';
  }
}

/**
 * Extract node data from proto for React Flow node
 */
function extractNodeData(pipelineNode: PipelineNode): Record<string, unknown> {
  const nodeType = pipelineNode.nodeType;
  switch (nodeType.case) {
    case 'streamSource':
      return {
        topicPath: nodeType.value.dataStream?.path ?? '',
        unwrapCdc: nodeType.value.unwrapCdc ?? false,
      };
    case 'sink': {
      // Extract topic name from full path
      const outputPath = nodeType.value.output?.path ?? '';
      const topicName = outputPath.split('/').pop() ?? '';
      return { topicName };
    }
    case 'geoIp':
      return {
        ipField: nodeType.value.ipField,
        outputField: nodeType.value.outputField || 'country_code',
      };
    case 'inspector':
      return { label: nodeType.value.label || '' };
    case 'group':
      return {
        aggregationType: 'count',
        groupByField: nodeType.value.keyMapperExpr.replace(/^\./, ''), // Remove leading dot
      };
    case 'textExtractor':
      return {
        filePathField: nodeType.value.filePathField,
        outputField: nodeType.value.outputField || 'text',
      };
    case 'embeddingGenerator':
      return {
        textField: nodeType.value.textField,
        outputField: nodeType.value.outputField || 'embedding',
        model: nodeType.value.model || 'text-embedding-3-small',
      };
    case 'openAiTransformer':
      return {
        prompt: nodeType.value.prompt,
        outputField: nodeType.value.outputField || 'ai_response',
        model: nodeType.value.model || 'gpt-4o-mini',
      };
    default:
      return { label: getNodeLabel(pipelineNode) };
  }
}

/**
 * Apply Dagre layout to nodes and edges for left-to-right graph visualization.
 */
function applyDagreLayout(nodes: Node[], edges: Edge[]): Node[] {
  const g = new Dagre.graphlib.Graph().setDefaultEdgeLabel(() => ({}));

  // Configure layout: left-to-right, generous spacing
  g.setGraph({
    rankdir: 'LR',
    nodesep: 80,
    ranksep: 120,
    marginx: 50,
    marginy: 50,
  });

  // Add nodes to the graph
  const nodeWidth = 200;
  const nodeHeight = 100;
  nodes.forEach((node) => {
    g.setNode(node.id, { width: nodeWidth, height: nodeHeight });
  });

  // Add edges to the graph
  edges.forEach((edge) => {
    g.setEdge(edge.source, edge.target);
  });

  // Run the layout
  Dagre.layout(g);

  // Apply positions to nodes
  return nodes.map((node) => {
    const nodeWithPosition = g.node(node.id);
    return {
      ...node,
      position: {
        x: nodeWithPosition.x - nodeWidth / 2,
        y: nodeWithPosition.y - nodeHeight / 2,
      },
    };
  });
}

/**
 * Deserialize a PipelineGraph proto to React Flow nodes and edges.
 * Applies Dagre auto-layout since the server doesn't store positions.
 */
export function deserializeGraph(graph: PipelineGraph): DeserializedGraph {
  // Convert proto nodes to React Flow nodes
  const nodes: Node[] = graph.nodes.map((pipelineNode) => ({
    id: pipelineNode.id,
    type: getReactFlowNodeType(pipelineNode),
    position: { x: 0, y: 0 }, // Will be set by Dagre
    data: {
      ...extractNodeData(pipelineNode),
      // For viewer mode, mark as read-only
      readOnly: true,
      label: getNodeLabel(pipelineNode),
    },
  }));

  // Convert proto edges to React Flow edges
  const edges: Edge[] = graph.edges.map((pipelineEdge, index) => ({
    id: `e${index}-${pipelineEdge.fromId}-${pipelineEdge.toId}`,
    source: pipelineEdge.fromId,
    target: pipelineEdge.toId,
  }));

  // Apply Dagre layout
  const layoutedNodes = applyDagreLayout(nodes, edges);

  return {
    nodes: layoutedNodes,
    edges,
  };
}

/**
 * Get node category statistics for debugging
 */
export function getGraphStats(graph: PipelineGraph): {
  sources: number;
  transforms: number;
  sinks: number;
} {
  const stats = { sources: 0, transforms: 0, sinks: 0 };
  graph.nodes.forEach((node) => {
    const category = getNodeCategory(node);
    stats[`${category}s` as keyof typeof stats]++;
  });
  return stats;
}
