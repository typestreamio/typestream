import type { Edge, Node } from '@xyflow/react';
import {
  PipelineGraph,
  PipelineNode,
  PipelineEdge,
  StreamSourceNode,
  SinkNode,
  DataStreamProto,
  GeoIpNode as GeoIpNodeProto,
} from '../generated/job_pb';
import type { KafkaSourceNodeData, KafkaSinkNodeData, GeoIpNodeData } from '../components/graph-builder/nodes';

export function serializeGraph(nodes: Node[], edges: Edge[]): PipelineGraph {
  const pipelineNodes: PipelineNode[] = nodes.map((node) => {
    if (node.type === 'kafkaSource') {
      const data = node.data as KafkaSourceNodeData;
      return new PipelineNode({
        id: node.id,
        nodeType: {
          case: 'streamSource',
          value: new StreamSourceNode({
            dataStream: new DataStreamProto({ path: data.topicPath }),
            // Encoding is auto-detected from Schema Registry by the backend
          }),
        },
      });
    }

    if (node.type === 'kafkaSink') {
      const data = node.data as KafkaSinkNodeData;
      const fullPath = `/dev/kafka/local/topics/${data.topicName}`;
      return new PipelineNode({
        id: node.id,
        nodeType: {
          case: 'sink',
          value: new SinkNode({
            output: new DataStreamProto({ path: fullPath }),
            // Encoding is propagated from source by the backend
          }),
        },
      });
    }

    throw new Error(`Unknown node type: ${node.type}`);
  });

  const pipelineEdges: PipelineEdge[] = edges.map(
    (edge) =>
      new PipelineEdge({
        fromId: edge.source,
        toId: edge.target,
      })
  );

  return new PipelineGraph({
    nodes: pipelineNodes,
    edges: pipelineEdges,
  });
}
