import type { Edge, Node } from '@xyflow/react';
import {
  PipelineGraph,
  PipelineNode,
  PipelineEdge,
  StreamSourceNode,
  SinkNode,
  DataStreamProto,
} from '../generated/job_pb';
import type { KafkaSourceNodeData, KafkaSinkNodeData } from '../components/graph-builder/nodes';

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
            encoding: data.encoding,
          }),
        },
      });
    }

    if (node.type === 'kafkaSink') {
      const data = node.data as KafkaSinkNodeData;
      return new PipelineNode({
        id: node.id,
        nodeType: {
          case: 'sink',
          value: new SinkNode({
            output: new DataStreamProto({ path: data.topicPath }),
            encoding: data.encoding,
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
