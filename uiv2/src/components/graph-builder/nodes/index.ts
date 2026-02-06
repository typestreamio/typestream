import type { Node, NodeTypes } from '@xyflow/react';
import { KafkaSourceNode, kafkaSourceRole } from './KafkaSourceNode';
import { PostgresSourceNode, postgresSourceRole } from './PostgresSourceNode';
import { KafkaSinkNode, kafkaSinkRole } from './KafkaSinkNode';
import { GeoIpNode, geoIpRole } from './GeoIpNode';
import { InspectorNode, inspectorRole } from './InspectorNode';
import { MaterializedViewNode, materializedViewRole, type AggregationType } from './MaterializedViewNode';
import { DbSinkNode, dbSinkRole } from './DbSinkNode';
import { WeaviateSinkNode, weaviateSinkRole } from './WeaviateSinkNode';
import { ElasticsearchSinkNode, elasticsearchSinkRole } from './ElasticsearchSinkNode';
import { TextExtractorNode, textExtractorRole } from './TextExtractorNode';
import { EmbeddingGeneratorNode, embeddingGeneratorRole } from './EmbeddingGeneratorNode';
import { OpenAiTransformerNode, openAiTransformerRole } from './OpenAiTransformerNode';
import { FilterNode, filterRole } from './FilterNode';

// Schema field with type information
export interface SchemaField {
  name: string;
  type: string;  // e.g., "String", "Long", "Optional<DateTime>"
}

// Common validation state for all nodes - populated by schema inference
export interface NodeValidationState {
  outputSchema?: SchemaField[];  // Computed output fields with types for this node
  schemaError?: string;          // Validation error message
  isInferring?: boolean;         // Loading indicator during inference
}

// Field type categories for dropdown compatibility
export type FieldTypeCategory = 'string' | 'numeric' | 'any';

// Node field requirements - specifies what type category each field input expects
export const nodeFieldRequirements: Record<string, Record<string, FieldTypeCategory>> = {
  geoIp: { ipField: 'string' },
  textExtractor: { filePathField: 'string' },
  embeddingGenerator: { textField: 'string' },
  materializedView: { groupByField: 'any' },
  dbSink: { primaryKeyFields: 'any' },
};

// Check if a field type is compatible with a required type category
export function isTypeCompatible(fieldType: string, required: FieldTypeCategory): boolean {
  if (required === 'any') return true;
  if (required === 'string') {
    return fieldType === 'String' || fieldType.startsWith('Optional<String');
  }
  if (required === 'numeric') {
    return ['Int', 'Long', 'Float', 'Double', 'Decimal'].some(t => fieldType.includes(t));
  }
  return false;
}

export interface KafkaSourceNodeData extends Record<string, unknown>, NodeValidationState {
  topicPath: string;
  unwrapCdc?: boolean;  // Extract 'after' payload from CDC envelope
}

export interface PostgresSourceNodeData extends Record<string, unknown>, NodeValidationState {
  connectionId: string;        // Reference to the connection
  connectionName: string;      // Display name
  topicPath: string;           // Full path to the Debezium topic
  tableName: string;           // Table name (extracted from topic)
  schemaName: string;          // Schema name (extracted from topic)
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
  enableWindowing?: boolean;
  windowSizeSeconds?: number;
}



// DbSinkNode - uses a pre-configured connection (credentials resolved server-side)
export interface DbSinkNodeData extends Record<string, unknown>, NodeValidationState {
  connectionId: string;        // Reference to the connection (server resolves credentials)
  connectionName: string;      // Display name
  databaseType: 'postgres' | 'mysql';
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

// WeaviateSinkNode - uses a pre-configured Weaviate connection
export interface WeaviateSinkNodeData extends Record<string, unknown>, NodeValidationState {
  connectionId: string;
  connectionName: string;
  collectionName: string;
  documentIdStrategy: 'NoIdStrategy' | 'KafkaIdStrategy' | 'FieldIdStrategy';
  documentIdField: string;
  vectorStrategy: 'NoVectorStrategy' | 'FieldVectorStrategy';
  vectorField: string;
  timestampField: string;  // Optional: field name for timestamp conversion (empty = no transform)
}

// ElasticsearchSinkNode - uses a pre-configured Elasticsearch connection
export interface ElasticsearchSinkNodeData extends Record<string, unknown>, NodeValidationState {
  connectionId: string;
  connectionName: string;
  indexName: string;
  documentIdStrategy: 'RECORD_KEY' | 'TOPIC_PARTITION_OFFSET';
  writeMethod: 'INSERT' | 'UPSERT';
  behaviorOnNullValues: 'IGNORE' | 'DELETE' | 'FAIL';
}

export interface FilterNodeData extends Record<string, unknown>, NodeValidationState {
  expression: string;
}

export type KafkaSourceNodeType = Node<KafkaSourceNodeData, 'kafkaSource'>;
export type PostgresSourceNodeType = Node<PostgresSourceNodeData, 'postgresSource'>;
export type KafkaSinkNodeType = Node<KafkaSinkNodeData, 'kafkaSink'>;
export type GeoIpNodeType = Node<GeoIpNodeData, 'geoIp'>;
export type InspectorNodeType = Node<InspectorNodeData, 'inspector'>;
export type MaterializedViewNodeType = Node<MaterializedViewNodeData, 'materializedView'>;
export type DbSinkNodeType = Node<DbSinkNodeData, 'dbSink'>;
export type WeaviateSinkNodeType = Node<WeaviateSinkNodeData, 'weaviateSink'>;
export type ElasticsearchSinkNodeType = Node<ElasticsearchSinkNodeData, 'elasticsearchSink'>;
export type TextExtractorNodeType = Node<TextExtractorNodeData, 'textExtractor'>;
export type EmbeddingGeneratorNodeType = Node<EmbeddingGeneratorNodeData, 'embeddingGenerator'>;
export type OpenAiTransformerNodeType = Node<OpenAiTransformerNodeData, 'openAiTransformer'>;
export type FilterNodeType = Node<FilterNodeData, 'filter'>;

export type AppNode = KafkaSourceNodeType | PostgresSourceNodeType | KafkaSinkNodeType | GeoIpNodeType | InspectorNodeType | MaterializedViewNodeType | DbSinkNodeType | WeaviateSinkNodeType | ElasticsearchSinkNodeType | TextExtractorNodeType | EmbeddingGeneratorNodeType | OpenAiTransformerNodeType | FilterNodeType;

export const nodeTypes: NodeTypes = {
  kafkaSource: KafkaSourceNode,
  postgresSource: PostgresSourceNode,
  kafkaSink: KafkaSinkNode,
  geoIp: GeoIpNode,
  inspector: InspectorNode,
  materializedView: MaterializedViewNode,
  dbSink: DbSinkNode,
  weaviateSink: WeaviateSinkNode,
  elasticsearchSink: ElasticsearchSinkNode,
  textExtractor: TextExtractorNode,
  embeddingGenerator: EmbeddingGeneratorNode,
  openAiTransformer: OpenAiTransformerNode,
  filter: FilterNode,
};

// Node roles: 'source' (no input), 'transform' (both), 'sink' (no output)
export type NodeRole = 'source' | 'transform' | 'sink';

const nodeRoles: Record<string, NodeRole> = {
  kafkaSource: kafkaSourceRole,
  postgresSource: postgresSourceRole,
  kafkaSink: kafkaSinkRole,
  geoIp: geoIpRole,
  inspector: inspectorRole,
  materializedView: materializedViewRole,
  dbSink: dbSinkRole,
  weaviateSink: weaviateSinkRole,
  elasticsearchSink: elasticsearchSinkRole,
  textExtractor: textExtractorRole,
  embeddingGenerator: embeddingGeneratorRole,
  openAiTransformer: openAiTransformerRole,
  filter: filterRole,
};

/** Check if a node type has an output handle (sources and transforms have outputs) */
export function nodeHasOutput(nodeType: string | undefined): boolean {
  if (!nodeType) return false;
  const role = nodeRoles[nodeType];
  return role === 'source' || role === 'transform';
}

/** Check if a node type has an input handle (transforms and sinks have inputs) */
export function nodeHasInput(nodeType: string | undefined): boolean {
  if (!nodeType) return false;
  const role = nodeRoles[nodeType];
  return role === 'transform' || role === 'sink';
}
