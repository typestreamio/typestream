import type { Edge, Node } from '@xyflow/react';
import {
  PipelineGraph,
  PipelineNode,
  PipelineEdge,
  StreamSourceNode,
  SinkNode,
  InspectorNode,
  DataStreamProto,
  GeoIpNode as GeoIpNodeProto,
  TextExtractorNode as TextExtractorNodeProto,
  EmbeddingGeneratorNode as EmbeddingGeneratorNodeProto,
  GroupNode,
  CountNode,
  ReduceLatestNode,
} from '../generated/job_pb';
import type { KafkaSourceNodeData, KafkaSinkNodeData, GeoIpNodeData, InspectorNodeData, MaterializedViewNodeData, TextExtractorNodeData, EmbeddingGeneratorNodeData } from '../components/graph-builder/nodes';

export function serializeGraph(nodes: Node[], edges: Edge[]): PipelineGraph {
  const pipelineNodes: PipelineNode[] = [];
  const pipelineEdges: PipelineEdge[] = [];

  // Process each node
  nodes.forEach((node) => {
    if (node.type === 'kafkaSource') {
      const data = node.data as KafkaSourceNodeData;
      pipelineNodes.push(new PipelineNode({
        id: node.id,
        nodeType: {
          case: 'streamSource',
          value: new StreamSourceNode({
            dataStream: new DataStreamProto({ path: data.topicPath }),
            // Encoding is auto-detected from Schema Registry by the backend
          }),
        },
      }));
      return;
    }

    if (node.type === 'kafkaSink') {
      const data = node.data as KafkaSinkNodeData;
      const fullPath = `/dev/kafka/local/topics/${data.topicName}`;
      pipelineNodes.push(new PipelineNode({
        id: node.id,
        nodeType: {
          case: 'sink',
          value: new SinkNode({
            output: new DataStreamProto({ path: fullPath }),
            // Encoding is propagated from source by the backend
          }),
        },
      }));
      return;
    }

    if (node.type === 'geoIp') {
      const data = node.data as GeoIpNodeData;
      pipelineNodes.push(new PipelineNode({
        id: node.id,
        nodeType: {
          case: 'geoIp',
          value: new GeoIpNodeProto({
            ipField: data.ipField,
            outputField: data.outputField || 'country_code',
          }),
        },
      }));
      return;
    }

    if (node.type === 'materializedView') {
      const data = node.data as MaterializedViewNodeData;
      const groupId = `${node.id}-group`;

      // Create Group node
      pipelineNodes.push(new PipelineNode({
        id: groupId,
        nodeType: {
          case: 'group',
          value: new GroupNode({ keyMapperExpr: `.${data.groupByField}` }),
        },
      }));

      // Create aggregation node based on type
      if (data.aggregationType === 'count') {
        pipelineNodes.push(new PipelineNode({
          id: node.id,
          nodeType: { case: 'count', value: new CountNode({}) },
        }));
      } else {
        pipelineNodes.push(new PipelineNode({
          id: node.id,
          nodeType: { case: 'reduceLatest', value: new ReduceLatestNode({}) },
        }));
      }

      // Internal edge from group to aggregation
      pipelineEdges.push(new PipelineEdge({ fromId: groupId, toId: node.id }));
      return;
    }

    if (node.type === 'inspector') {
      const data = node.data as InspectorNodeData;
      pipelineNodes.push(new PipelineNode({
        id: node.id,
        nodeType: {
          case: 'inspector',
          value: new InspectorNode({
            label: data.label || '',
          }),
        },
      }));
      return;
    }

    if (node.type === 'textExtractor') {
      const data = node.data as TextExtractorNodeData;
      pipelineNodes.push(new PipelineNode({
        id: node.id,
        nodeType: {
          case: 'textExtractor',
          value: new TextExtractorNodeProto({
            filePathField: data.filePathField,
            outputField: data.outputField || 'text',
          }),
        },
      }));
      return;
    }

    if (node.type === 'embeddingGenerator') {
      const data = node.data as EmbeddingGeneratorNodeData;
      pipelineNodes.push(new PipelineNode({
        id: node.id,
        nodeType: {
          case: 'embeddingGenerator',
          value: new EmbeddingGeneratorNodeProto({
            textField: data.textField,
            outputField: data.outputField || 'embedding',
            model: data.model || 'text-embedding-3-small',
          }),
        },
      }));
      return;
    }

    throw new Error(`Unknown node type: ${node.type}`);
  });

  // Process edges - redirect edges targeting materializedView to its group node
  edges.forEach((edge) => {
    const targetNode = nodes.find((n) => n.id === edge.target);
    const toId = targetNode?.type === 'materializedView' ? `${edge.target}-group` : edge.target;
    pipelineEdges.push(new PipelineEdge({ fromId: edge.source, toId }));
  });

  return new PipelineGraph({
    nodes: pipelineNodes,
    edges: pipelineEdges,
  });
}
