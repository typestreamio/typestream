import type { Node, NodeTypes } from '@xyflow/react';
import { KafkaSourceNode } from './KafkaSourceNode';
import { KafkaSinkNode } from './KafkaSinkNode';
import { InspectorNode } from './InspectorNode';

export interface KafkaSourceNodeData extends Record<string, unknown> {
  topicPath: string;
}

export interface KafkaSinkNodeData extends Record<string, unknown> {
  topicName: string;
}

export interface InspectorNodeData extends Record<string, unknown> {
  label?: string;
}

export type KafkaSourceNodeType = Node<KafkaSourceNodeData, 'kafkaSource'>;
export type KafkaSinkNodeType = Node<KafkaSinkNodeData, 'kafkaSink'>;
export type InspectorNodeType = Node<InspectorNodeData, 'inspector'>;

export type AppNode = KafkaSourceNodeType | KafkaSinkNodeType | InspectorNodeType;

export const nodeTypes: NodeTypes = {
  kafkaSource: KafkaSourceNode,
  kafkaSink: KafkaSinkNode,
  inspector: InspectorNode,
};
