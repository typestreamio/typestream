import type { Edge, Node } from '@xyflow/react';
import { PipelineGraph } from '../generated/job_pb';
import { serializeGraph } from './graphSerializer';
import { nodeHasOutput } from '../components/graph-builder/nodes';

/**
 * Result of augmenting a graph for preview
 */
export interface AugmentedGraphResult {
  graph: PipelineGraph;
  inspectorNodeId: string;
}

/**
 * Augment a graph with a temporary inspector node connected to the target node.
 *
 * For nodes with outputs (sources/transforms): Connect inspector to the node's output
 * For sink nodes (no outputs): Connect inspector between upstream node and the sink
 *
 * @param nodes - React Flow nodes
 * @param edges - React Flow edges
 * @param targetNodeId - The node to preview
 * @returns Augmented graph with temporary inspector and the inspector's node ID
 */
export function augmentGraphForPreview(
  nodes: Node[],
  edges: Edge[],
  targetNodeId: string
): AugmentedGraphResult {
  const targetNode = nodes.find(n => n.id === targetNodeId);
  if (!targetNode) {
    throw new Error(`Target node not found: ${targetNodeId}`);
  }

  // If the target IS an inspector node, just use the existing graph
  if (targetNode.type === 'inspector') {
    return {
      graph: serializeGraph(nodes, edges),
      inspectorNodeId: targetNodeId,
    };
  }

  const tempInspectorId = `__preview_inspector_${targetNodeId}`;

  // Create temporary inspector node
  const tempInspectorNode: Node = {
    id: tempInspectorId,
    type: 'inspector',
    position: { x: 0, y: 0 }, // Position doesn't matter for serialization
    data: { label: 'Preview' },
  };

  // Determine how to connect the inspector based on node type
  const hasOutput = nodeHasOutput(targetNode.type);

  let augmentedNodes: Node[];
  let augmentedEdges: Edge[];

  if (hasOutput) {
    // Node has output: connect inspector directly to target's output
    augmentedNodes = [...nodes, tempInspectorNode];
    augmentedEdges = [
      ...edges,
      {
        id: `__preview_edge_${targetNodeId}`,
        source: targetNodeId,
        target: tempInspectorId,
      },
    ];
  } else {
    // Sink node (no output): insert inspector between upstream and sink
    // Find the edge going INTO this sink
    const incomingEdge = edges.find(e => e.target === targetNodeId);

    if (!incomingEdge) {
      throw new Error(`Sink node ${targetNodeId} has no incoming connection`);
    }

    // Remove the original edge and insert inspector in between
    const upstreamNodeId = incomingEdge.source;

    augmentedNodes = [...nodes, tempInspectorNode];
    augmentedEdges = [
      // Keep all edges except the one going to the sink
      ...edges.filter(e => e.target !== targetNodeId),
      // Upstream -> Inspector
      {
        id: `__preview_edge_upstream_${targetNodeId}`,
        source: upstreamNodeId,
        target: tempInspectorId,
      },
      // Inspector -> Sink (keep the data flowing)
      {
        id: `__preview_edge_sink_${targetNodeId}`,
        source: tempInspectorId,
        target: targetNodeId,
      },
    ];
  }

  return {
    graph: serializeGraph(augmentedNodes, augmentedEdges),
    inspectorNodeId: tempInspectorId,
  };
}
