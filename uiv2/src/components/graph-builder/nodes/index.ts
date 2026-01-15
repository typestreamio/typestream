import type { Node, NodeTypes } from '@xyflow/react';
import { KafkaSourceNode } from './KafkaSourceNode';
import { KafkaSinkNode } from './KafkaSinkNode';
import { GeoIpNode } from './GeoIpNode';
import { InspectorNode } from './InspectorNode';
import { MaterializedViewNode, type AggregationType } from './MaterializedViewNode';
import { TextExtractorNode } from './TextExtractorNode';
import { EmbeddingGeneratorNode } from './EmbeddingGeneratorNode';

// Common validation state for all nodes - populated by schema inference
export interface NodeValidationState {
  outputSchema?: string[];  // Computed output fields for this node
  schemaError?: string;     // Validation error message
  isInferring?: boolean;    // Loading indicator during inference
}

export interface KafkaSourceNodeData extends Record<string, unknown>, NodeValidationState {
  topicPath: string;
}

export interface KafkaSinkNodeData extends Record<string, unknown>, NodeValidationState {
  topicName: string;
}

export interface GeoIpNodeData extends Record<string, unknown>, NodeValidationState {
  ipField: string;
  outputField: string;
}

export interface InspectorNodeData extends Record<string, unknown>, NodeValidationState {
  label?: string;
}

export interface MaterializedViewNodeData extends Record<string, unknown>, NodeValidationState {
  aggregationType: AggregationType;
  groupByField: string;
}

export interface TextExtractorNodeData extends Record<string, unknown>, NodeValidationState {
  filePathField: string;
  outputField: string;
}

export interface EmbeddingGeneratorNodeData extends Record<string, unknown>, NodeValidationState {
  textField: string;
  outputField: string;
  model: string;
}

export type KafkaSourceNodeType = Node<KafkaSourceNodeData, 'kafkaSource'>;
export type KafkaSinkNodeType = Node<KafkaSinkNodeData, 'kafkaSink'>;
export type GeoIpNodeType = Node<GeoIpNodeData, 'geoIp'>;
export type InspectorNodeType = Node<InspectorNodeData, 'inspector'>;
export type MaterializedViewNodeType = Node<MaterializedViewNodeData, 'materializedView'>;
export type TextExtractorNodeType = Node<TextExtractorNodeData, 'textExtractor'>;
export type EmbeddingGeneratorNodeType = Node<EmbeddingGeneratorNodeData, 'embeddingGenerator'>;

export type AppNode = KafkaSourceNodeType | KafkaSinkNodeType | GeoIpNodeType | InspectorNodeType | MaterializedViewNodeType | TextExtractorNodeType | EmbeddingGeneratorNodeType;

export const nodeTypes: NodeTypes = {
  kafkaSource: KafkaSourceNode,
  kafkaSink: KafkaSinkNode,
  geoIp: GeoIpNode,
  inspector: InspectorNode,
  materializedView: MaterializedViewNode,
  textExtractor: TextExtractorNode,
  embeddingGenerator: EmbeddingGeneratorNode,
};
