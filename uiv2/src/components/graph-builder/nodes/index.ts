import type { Node, NodeTypes } from '@xyflow/react';
import { KafkaSourceNode } from './KafkaSourceNode';
import { KafkaSinkNode } from './KafkaSinkNode';
import { GeoIpNode } from './GeoIpNode';
import { InspectorNode } from './InspectorNode';
import { MaterializedViewNode, type AggregationType } from './MaterializedViewNode';
import { JDBCSinkNode } from './JDBCSinkNode';
import { DbSinkNode } from './DbSinkNode';
import { TextExtractorNode } from './TextExtractorNode';
import { EmbeddingGeneratorNode } from './EmbeddingGeneratorNode';
import { OpenAiTransformerNode } from './OpenAiTransformerNode';

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

export interface JDBCSinkNodeData extends Record<string, unknown>, NodeValidationState {
  databaseType: 'postgres' | 'mysql';
  hostname: string;
  port: string;
  database: string;
  username: string;
  password: string;
  tableName: string;
  insertMode: 'insert' | 'upsert' | 'update';
  primaryKeyFields: string;
}

// DbSinkNode - uses a pre-configured connection (full config stored in node)
export interface DbSinkNodeData extends Record<string, unknown>, NodeValidationState {
  connectionId: string;        // Reference to the connection
  connectionName: string;      // Display name
  databaseType: 'postgres' | 'mysql';
  // Full connection config for JDBC sink connector creation
  hostname: string;
  connectorHostname: string;   // Docker network hostname for Kafka Connect
  port: string;
  database: string;
  username: string;
  password: string;
  // User-specified per-node
  tableName: string;
  insertMode: 'insert' | 'upsert' | 'update';
  primaryKeyFields: string;
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

export interface OpenAiTransformerNodeData extends Record<string, unknown>, NodeValidationState {
  prompt: string;
  outputField: string;
  model: string;
}

export type KafkaSourceNodeType = Node<KafkaSourceNodeData, 'kafkaSource'>;
export type KafkaSinkNodeType = Node<KafkaSinkNodeData, 'kafkaSink'>;
export type GeoIpNodeType = Node<GeoIpNodeData, 'geoIp'>;
export type InspectorNodeType = Node<InspectorNodeData, 'inspector'>;
export type MaterializedViewNodeType = Node<MaterializedViewNodeData, 'materializedView'>;
export type JDBCSinkNodeType = Node<JDBCSinkNodeData, 'jdbcSink'>;
export type DbSinkNodeType = Node<DbSinkNodeData, 'dbSink'>;
export type TextExtractorNodeType = Node<TextExtractorNodeData, 'textExtractor'>;
export type EmbeddingGeneratorNodeType = Node<EmbeddingGeneratorNodeData, 'embeddingGenerator'>;
export type OpenAiTransformerNodeType = Node<OpenAiTransformerNodeData, 'openAiTransformer'>;

export type AppNode = KafkaSourceNodeType | KafkaSinkNodeType | GeoIpNodeType | InspectorNodeType | MaterializedViewNodeType | JDBCSinkNodeType | DbSinkNodeType | TextExtractorNodeType | EmbeddingGeneratorNodeType | OpenAiTransformerNodeType;

export const nodeTypes: NodeTypes = {
  kafkaSource: KafkaSourceNode,
  kafkaSink: KafkaSinkNode,
  geoIp: GeoIpNode,
  inspector: InspectorNode,
  materializedView: MaterializedViewNode,
  jdbcSink: JDBCSinkNode,
  dbSink: DbSinkNode,
  textExtractor: TextExtractorNode,
  embeddingGenerator: EmbeddingGeneratorNode,
  openAiTransformer: OpenAiTransformerNode,
};
