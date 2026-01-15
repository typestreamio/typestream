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
  GroupNode,
  CountNode,
  ReduceLatestNode,
} from '../generated/job_pb';
import type { KafkaSourceNodeData, KafkaSinkNodeData, GeoIpNodeData, InspectorNodeData, MaterializedViewNodeData, JDBCSinkNodeData } from '../components/graph-builder/nodes';

/**
 * Configuration for JDBC sink connectors that need to be created
 * after the job is successfully created
 */
export interface JDBCSinkConnectorConfig {
  nodeId: string;
  intermediateTopic: string;
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

/**
 * Result of serializing a graph, including both the proto and
 * any JDBC sink connectors that need to be created
 */
export interface SerializedGraph {
  graph: PipelineGraph;
  jdbcSinkConnectors: JDBCSinkConnectorConfig[];
}

/**
 * Generate a unique topic name for intermediate JDBC sink output
 */
function generateIntermediateTopicName(nodeId: string): string {
  const timestamp = Date.now();
  const sanitizedNodeId = nodeId.replace(/[^a-zA-Z0-9]/g, '-');
  return `jdbc-sink-${sanitizedNodeId}-${timestamp}`;
}

/**
 * Serialize a graph to a PipelineGraph proto and extract JDBC sink configurations
 */
export function serializeGraphWithSinks(nodes: Node[], edges: Edge[]): SerializedGraph {
  const pipelineNodes: PipelineNode[] = [];
  const pipelineEdges: PipelineEdge[] = [];
  const jdbcSinkConnectors: JDBCSinkConnectorConfig[] = [];

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

    if (node.type === 'jdbcSink') {
      const data = node.data as JDBCSinkNodeData;
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

      // Record the JDBC sink configuration for later connector creation
      jdbcSinkConnectors.push({
        nodeId: node.id,
        intermediateTopic,
        databaseType: data.databaseType || 'postgres',
        hostname: data.hostname || '',
        port: data.port || '5432',
        database: data.database || '',
        username: data.username || '',
        password: data.password || '',
        tableName: data.tableName || '',
        insertMode: data.insertMode || 'upsert',
        primaryKeyFields: data.primaryKeyFields || '',
      });
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
    jdbcSinkConnectors,
  };
}

/**
 * Serialize a graph to a PipelineGraph proto (backward compatible)
 */
export function serializeGraph(nodes: Node[], edges: Edge[]): PipelineGraph {
  return serializeGraphWithSinks(nodes, edges).graph;
}
