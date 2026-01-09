import type { Node, NodeTypes } from '@xyflow/react';
import { Encoding } from '../../../generated/job_pb';
import { KafkaSourceNode } from './KafkaSourceNode';
import { KafkaSinkNode } from './KafkaSinkNode';

export interface KafkaSourceNodeData {
  topicPath: string;
  encoding: Encoding;
}

export interface KafkaSinkNodeData {
  topicPath: string;
  encoding: Encoding;
}

export type KafkaSourceNodeType = Node<KafkaSourceNodeData, 'kafkaSource'>;
export type KafkaSinkNodeType = Node<KafkaSinkNodeData, 'kafkaSink'>;

export type AppNode = KafkaSourceNodeType | KafkaSinkNodeType;

export const nodeTypes: NodeTypes = {
  kafkaSource: KafkaSourceNode,
  kafkaSink: KafkaSinkNode,
};

export const ENCODING_OPTIONS = [
  { value: Encoding.STRING, label: 'STRING' },
  { value: Encoding.JSON, label: 'JSON' },
  { value: Encoding.AVRO, label: 'AVRO' },
  { value: Encoding.PROTOBUF, label: 'PROTOBUF' },
];
