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
  OpenAiTransformerNode as OpenAiTransformerNodeProto,
  FilterNode as FilterNodeProto,
  PredicateProto,
  GroupNode,
  CountNode,
  ReduceLatestNode,
  DbSinkConfig,
  WeaviateSinkConfig,
} from '../generated/job_pb';
import type { KafkaSourceNodeData, KafkaSinkNodeData, GeoIpNodeData, InspectorNodeData, MaterializedViewNodeData, DbSinkNodeData, WeaviateSinkNodeData, TextExtractorNodeData, EmbeddingGeneratorNodeData, OpenAiTransformerNodeData, FilterNodeData } from '../components/graph-builder/nodes';

/**
 * Result of serializing a graph with sink connectors
 */
export interface SerializedGraphWithSinks {
  graph: PipelineGraph;
  dbSinkConfigs: DbSinkConfig[];  // Proto objects ready for request
  weaviateSinkConfigs: WeaviateSinkConfig[];  // Proto objects ready for request
}

// Re-export for backward compatibility
export interface SerializedGraphWithDbSinks {
  graph: PipelineGraph;
  dbSinkConfigs: DbSinkConfig[];
}

// Re-export for consumers that need the type
export { DbSinkConfig, WeaviateSinkConfig };

/**
 * Generate a unique topic name for intermediate JDBC sink output
 */
function generateIntermediateTopicName(nodeId: string): string {
  const timestamp = Date.now();
  const sanitizedNodeId = nodeId.replace(/[^a-zA-Z0-9]/g, '-');
  return `jdbc-sink-${sanitizedNodeId}-${timestamp}`;
}

/**
 * Generate a unique topic name for intermediate Weaviate sink output
 */
function generateWeaviateIntermediateTopicName(nodeId: string): string {
  const timestamp = Date.now();
  const sanitizedNodeId = nodeId.replace(/[^a-zA-Z0-9]/g, '-');
  return `weaviate-sink-${sanitizedNodeId}-${timestamp}`;
}

/**
 * Serialize a graph to a PipelineGraph proto and extract all sink configurations
 * (credentials resolved server-side for security)
 */
export function serializeGraphWithSinks(nodes: Node[], edges: Edge[]): SerializedGraphWithSinks {
  const pipelineNodes: PipelineNode[] = [];
  const pipelineEdges: PipelineEdge[] = [];
  const dbSinkConfigs: DbSinkConfig[] = [];
  const weaviateSinkConfigs: WeaviateSinkConfig[] = [];

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
            unwrapCdc: data.unwrapCdc ?? false,
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

    if (node.type === 'dbSink') {
      const data = node.data as DbSinkNodeData;

      // Generate an intermediate topic for the TypeStream job to write to
      const intermediateTopic = generateIntermediateTopicName(node.id);
      const fullPath = `/dev/kafka/local/topics/${intermediateTopic}`;

      // Create a regular SinkNode that writes to the intermediate topic
      pipelineNodes.push(new PipelineNode({
        id: node.id,
        nodeType: {
          case: 'sink',
          value: new SinkNode({
            output: new DataStreamProto({ path: fullPath }),
          }),
        },
      }));

      // Record the DB sink configuration as proto (credentials resolved server-side)
      dbSinkConfigs.push(new DbSinkConfig({
        nodeId: node.id,
        connectionId: data.connectionId,
        intermediateTopic,
        tableName: data.tableName || '',
        insertMode: data.insertMode || 'upsert',
        primaryKeyFields: data.primaryKeyFields || '',
      }));
      return;
    }

    if (node.type === 'weaviateSink') {
      const data = node.data as WeaviateSinkNodeData;

      // Generate an intermediate topic for the TypeStream job to write to
      const intermediateTopic = generateWeaviateIntermediateTopicName(node.id);
      const fullPath = `/dev/kafka/local/topics/${intermediateTopic}`;

      // Create a regular SinkNode that writes to the intermediate topic
      pipelineNodes.push(new PipelineNode({
        id: node.id,
        nodeType: {
          case: 'sink',
          value: new SinkNode({
            output: new DataStreamProto({ path: fullPath }),
          }),
        },
      }));

      // Record the Weaviate sink configuration as proto (credentials resolved server-side)
      weaviateSinkConfigs.push(new WeaviateSinkConfig({
        nodeId: node.id,
        connectionId: data.connectionId,
        intermediateTopic,
        collectionName: data.collectionName || '',
        documentIdStrategy: data.documentIdStrategy || 'NoIdStrategy',
        documentIdField: data.documentIdField || '',
        vectorStrategy: data.vectorStrategy || 'NoVectorStrategy',
        vectorField: data.vectorField || '',
        timestampField: data.timestampField || '',
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

    if (node.type === 'filter') {
      const data = node.data as FilterNodeData;
      pipelineNodes.push(new PipelineNode({
        id: node.id,
        nodeType: {
          case: 'filter',
          value: new FilterNodeProto({
            byKey: false,
            predicate: new PredicateProto({ expr: data.expression || '' }),
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

    if (node.type === 'openAiTransformer') {
      const data = node.data as OpenAiTransformerNodeData;
      pipelineNodes.push(new PipelineNode({
        id: node.id,
        nodeType: {
          case: 'openAiTransformer',
          value: new OpenAiTransformerNodeProto({
            prompt: data.prompt,
            outputField: data.outputField || 'ai_response',
            model: data.model || 'gpt-4o-mini',
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

  return {
    graph: new PipelineGraph({
      nodes: pipelineNodes,
      edges: pipelineEdges,
    }),
    dbSinkConfigs,
    weaviateSinkConfigs,
  };
}

/**
 * Serialize a graph with DB sinks only (backward compatible)
 */
export function serializeGraphWithDbSinks(nodes: Node[], edges: Edge[]): SerializedGraphWithDbSinks {
  const result = serializeGraphWithSinks(nodes, edges);
  return {
    graph: result.graph,
    dbSinkConfigs: result.dbSinkConfigs,
  };
}

/**
 * Serialize a graph to a PipelineGraph proto (backward compatible)
 */
export function serializeGraph(nodes: Node[], edges: Edge[]): PipelineGraph {
  return serializeGraphWithSinks(nodes, edges).graph;
}
