import type { NodeEditor } from 'rete';
import type { Schemes, TypeStreamNode } from '../types/rete';
import { PipelineGraph, PipelineEdge } from '../generated/job_pb';

export function serializeToPipelineGraph(editor: NodeEditor<Schemes>): PipelineGraph {
  const nodes = [];

  // Convert all nodes
  for (const node of editor.getNodes()) {
    const typedNode = node as unknown as TypeStreamNode;
    nodes.push(typedNode.toProto(node.id));
  }

  const edges: PipelineEdge[] = [];

  // Convert all connections
  for (const connection of editor.getConnections()) {
    edges.push(new PipelineEdge({
      fromId: connection.source,
      toId: connection.target
    }));
  }

  return new PipelineGraph({ nodes, edges });
}
