import { useCallback, useRef, useState, useEffect, useMemo } from 'react';
import {
  ReactFlow,
  Background,
  Controls,
  addEdge,
  useNodesState,
  useEdgesState,
  type OnConnect,
  type Edge,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Alert from '@mui/material/Alert';
import { useTheme } from '@mui/material/styles';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import { useNavigate } from 'react-router-dom';
import { NodePalette } from './NodePalette';
import { nodeTypes, nodeHasInput, nodeHasOutput, type AppNode } from './nodes';
import { serializeGraph, serializeGraphWithSinks } from '../../utils/graphSerializer';
import { getGraphDependencyKey } from '../../utils/graphDependencyKey';
import { useCreateJob } from '../../hooks/useCreateJob';
import { useInferGraphSchemas } from '../../hooks/useInferGraphSchemas';
import { CreateJobFromGraphRequest, InferGraphSchemasRequest } from '../../generated/job_pb';

let nodeId = 0;
const getId = () => `node-${nodeId++}`;

export function GraphBuilder() {
  const theme = useTheme();
  const navigate = useNavigate();
  const reactFlowWrapper = useRef<HTMLDivElement>(null);
  const [nodes, setNodes, onNodesChange] = useNodesState<AppNode>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);
  const [createError, setCreateError] = useState<string | null>(null);
  const createJob = useCreateJob();
  const inferSchemas = useInferGraphSchemas();

  // Create a stable dependency key that tracks meaningful graph changes
  // (excludes validation state fields that we set)
  const graphKey = useMemo(() => getGraphDependencyKey(nodes, edges), [nodes, edges]);

  // Keep a ref to the latest nodes/edges for use in the async callback
  const nodesRef = useRef(nodes);
  const edgesRef = useRef(edges);
  useEffect(() => {
    nodesRef.current = nodes;
    edgesRef.current = edges;
  }, [nodes, edges]);

  // Request counter to prevent stale responses from updating state
  const requestIdRef = useRef(0);

  // Debounced schema inference on graph changes
  useEffect(() => {
    if (nodes.length === 0) return;

    // Increment request ID for this inference cycle
    const currentRequestId = ++requestIdRef.current;

    // Mark all nodes as inferring
    setNodes((nds) =>
      nds.map((n) => ({
        ...n,
        data: { ...n.data, isInferring: true },
      } as AppNode))
    );

    const timeout = setTimeout(async () => {
      try {
        // Use refs to get latest values inside async callback
        const currentNodes = nodesRef.current;
        const currentEdges = edgesRef.current;
        const graph = serializeGraph(currentNodes, currentEdges);
        const request = new InferGraphSchemasRequest({ graph });
        const response = await inferSchemas.mutateAsync(request);

        // Only update state if this is still the latest request
        if (currentRequestId !== requestIdRef.current) return;

        // Update each node with its schema result
        setNodes((nds) =>
          nds.map((n) => {
            const result = response.schemas[n.id];
            // Use typed_fields for full type info, with fields as fallback for backward compat
            const outputSchema = result?.typedFields?.length
              ? result.typedFields.map(f => ({ name: f.name, type: f.type }))
              : result?.fields?.map(name => ({ name, type: 'Unknown' })) ?? [];
            return {
              ...n,
              data: {
                ...n.data,
                outputSchema,
                schemaError: result?.error || undefined,
                isInferring: false,
              },
            } as AppNode;
          })
        );
      } catch {
        // Only update state if this is still the latest request
        if (currentRequestId !== requestIdRef.current) return;

        // Network error - mark all nodes with error
        setNodes((nds) =>
          nds.map((n) => ({
            ...n,
            data: {
              ...n.data,
              schemaError: 'Schema inference failed',
              isInferring: false,
            },
          } as AppNode))
        );
      }
    }, 300);

    return () => clearTimeout(timeout);
    // graphKey changes when meaningful node/edge data changes (excluding validation state)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [graphKey, inferSchemas.mutateAsync]);

  const onConnect: OnConnect = useCallback(
    (params) => setEdges((eds) => addEdge({
      ...params,
      type: 'smoothstep',
      animated: false,
    }, eds)),
    [setEdges]
  );

  // Default edge options for consistent horizontal flow styling
  const defaultEdgeOptions = {
    type: 'smoothstep',
    animated: false,
  };

  const onDragOver = useCallback((event: React.DragEvent) => {
    event.preventDefault();
    event.dataTransfer.dropEffect = 'move';
  }, []);

  // Helper to get default node data based on type
  const getDefaultNodeData = useCallback((type: string, dragData: Record<string, unknown> = {}): Record<string, unknown> => {
    if (type === 'kafkaSink') {
      return { topicName: '' };
    } else if (type === 'geoIp') {
      return { ipField: '', outputField: 'country_code' };
    } else if (type === 'inspector') {
      return { label: '' };
    } else if (type === 'materializedView') {
      return { aggregationType: 'count', groupByField: '' };
    } else if (type === 'jdbcSink') {
      return {
        databaseType: 'postgres',
        hostname: '',
        port: '5432',
        database: '',
        username: '',
        password: '',
        tableName: '',
        insertMode: 'upsert',
        primaryKeyFields: '',
      };
    } else if (type === 'dbSink') {
      // DbSink node - only non-sensitive fields (credentials resolved server-side)
      return {
        connectionId: dragData.connectionId || '',
        connectionName: dragData.connectionName || '',
        databaseType: dragData.databaseType || 'postgres',
        tableName: '',
        insertMode: 'upsert',
        primaryKeyFields: '',
      };
    } else if (type === 'weaviateSink') {
      // WeaviateSink node - only non-sensitive fields (credentials resolved server-side)
      return {
        connectionId: dragData.connectionId || '',
        connectionName: dragData.connectionName || '',
        collectionName: dragData.collectionName || '',
        documentIdStrategy: dragData.documentIdStrategy || 'NoIdStrategy',
        documentIdField: dragData.documentIdField || '',
        vectorStrategy: dragData.vectorStrategy || 'NoVectorStrategy',
        vectorField: dragData.vectorField || '',
        timestampField: dragData.timestampField || '',
      };
    } else if (type === 'elasticsearchSink') {
      // ElasticsearchSink node - only non-sensitive fields (credentials resolved server-side)
      return {
        connectionId: dragData.connectionId || '',
        connectionName: dragData.connectionName || '',
        indexName: dragData.indexName || '',
        documentIdStrategy: dragData.documentIdStrategy || 'RECORD_KEY',
        writeMethod: dragData.writeMethod || 'INSERT',
        behaviorOnNullValues: dragData.behaviorOnNullValues || 'IGNORE',
      };
    } else if (type === 'textExtractor') {
      return { filePathField: '', outputField: 'text' };
    } else if (type === 'embeddingGenerator') {
      return { textField: '', outputField: 'embedding', model: 'text-embedding-3-small' };
    } else if (type === 'openAiTransformer') {
      return { prompt: '', outputField: 'ai_response', model: 'gpt-4o-mini' };
    } else if (type === 'filter') {
      return { expression: '' };
    } else if (type === 'postgresSource') {
      // Postgres source - connection info comes from drag data, table selected in node
      return {
        connectionId: dragData.connectionId || '',
        connectionName: dragData.connectionName || '',
        topicPath: '',
        tableName: '',
        schemaName: '',
      };
    } else {
      return { topicPath: '' };
    }
  }, []);

  const onDrop = useCallback(
    (event: React.DragEvent) => {
      event.preventDefault();

      const rawData = event.dataTransfer.getData('application/reactflow');
      if (!rawData || !reactFlowWrapper.current) return;

      const bounds = reactFlowWrapper.current.getBoundingClientRect();
      const position = {
        x: event.clientX - bounds.left - 100,
        y: event.clientY - bounds.top - 50,
      };

      // Try to parse as JSON (for nodes with extra data like dbSink)
      let type: string;
      let dragData: Record<string, unknown> = {};
      try {
        const parsed = JSON.parse(rawData);
        type = parsed.type;
        dragData = parsed;
      } catch {
        // Not JSON, treat as simple type string
        type = rawData;
      }

      const data = getDefaultNodeData(type, dragData);

      const newNode: AppNode = {
        id: getId(),
        type,
        position,
        data,
      } as AppNode;

      setNodes((nds) => [...nds, newNode]);
    },
    [setNodes, getDefaultNodeData]
  );

  // Add node from palette click (places to the right of existing nodes)
  const handleAddNode = useCallback(
    (type: string, dragData?: Record<string, unknown>) => {
      if (!reactFlowWrapper.current) return;

      const bounds = reactFlowWrapper.current.getBoundingClientRect();
      const existingNodes = nodesRef.current;

      // Fixed Y position (vertically centered in canvas)
      const fixedY = bounds.height / 2 - 50;

      // Calculate X position: 30px to the right of the rightmost node
      const DEFAULT_NODE_WIDTH = 220; // minWidth from BaseNode
      const NODE_GAP = 30;

      let newX: number;
      let rightmostNode: AppNode | null = null;
      if (existingNodes.length === 0) {
        // First node: start near left side with some padding
        newX = 50;
      } else {
        // Find the node whose right edge is furthest right
        let maxRightEdge = 0;
        for (const node of existingNodes) {
          // Use measured width if available, otherwise fall back to default
          const nodeWidth = node.measured?.width ?? node.width ?? DEFAULT_NODE_WIDTH;
          const rightEdge = node.position.x + nodeWidth;
          if (rightEdge > maxRightEdge) {
            maxRightEdge = rightEdge;
            rightmostNode = node;
          }
        }
        // New node starts 15px after the rightmost edge
        newX = maxRightEdge + NODE_GAP;
      }

      const position = {
        x: newX,
        y: fixedY,
      };

      const data = getDefaultNodeData(type, dragData || {});

      const newNodeId = getId();
      const newNode: AppNode = {
        id: newNodeId,
        type,
        position,
        data,
      } as AppNode;

      setNodes((nds) => [...nds, newNode]);

      // Auto-connect: if the rightmost node has an output and the new node has an input
      if (rightmostNode && nodeHasOutput(rightmostNode.type) && nodeHasInput(type)) {
        setEdges((eds) => addEdge({
          id: `edge-${rightmostNode.id}-${newNodeId}`,
          source: rightmostNode.id,
          target: newNodeId,
          type: 'smoothstep',
          animated: false,
        }, eds));
      }
    },
    [setNodes, setEdges, getDefaultNodeData]
  );

  const handleCreateJob = async () => {
    setCreateError(null);
    const { graph, dbSinkConfigs, weaviateSinkConfigs, elasticsearchSinkConfigs } = serializeGraphWithSinks(nodes, edges);

    // Single consolidated request - server handles both job + connectors atomically
    const request = new CreateJobFromGraphRequest({
      userId: 'local',
      graph,
      dbSinkConfigs,  // Server creates connectors and rolls back on failure
      weaviateSinkConfigs,  // Server creates Weaviate connectors and rolls back on failure
      elasticsearchSinkConfigs,  // Server creates Elasticsearch connectors and rolls back on failure
    });

    createJob.mutate(request, {
      onSuccess: (response) => {
        if (response.success && response.jobId) {
          // Log created connectors for visibility
          if (response.createdConnectors.length > 0) {
            console.log('Created connectors:', response.createdConnectors);
          }
          navigate(`/jobs/${response.jobId}`);
        } else if (response.error) {
          setCreateError(response.error);
        }
      },
    });
  };

  // Validate that all Weaviate sink nodes have required fields
  const allWeaviateSinksValid = nodes
    .filter((node) => node.type === 'weaviateSink')
    .every((node) => {
      const data = node.data as { collectionName?: string };
      return data.collectionName && data.collectionName.trim().length > 0;
    });

  // Validate that all Elasticsearch sink nodes have required fields
  const allElasticsearchSinksValid = nodes
    .filter((node) => node.type === 'elasticsearchSink')
    .every((node) => {
      const data = node.data as { indexName?: string };
      return data.indexName && data.indexName.trim().length > 0;
    });

  const canCreate = nodes.length > 0 && allWeaviateSinksValid && allElasticsearchSinksValid;

  return (
    <Box sx={{ display: 'flex', height: '100%', gap: 2, minHeight: 0 }}>
      <NodePalette onAddNode={handleAddNode} />
      <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 2, minHeight: 0 }}>
        {(createJob.isError || createError) && (
          <Alert severity="error">
            {createJob.isError ? createJob.error.message : createError}
          </Alert>
        )}
        <Box
          ref={reactFlowWrapper}
          sx={{
            flex: 1,
            minHeight: 0,
            border: '1px solid',
            borderColor: 'divider',
            borderRadius: 1,
            overflow: 'hidden',
          }}
        >
          <ReactFlow
            nodes={nodes}
            edges={edges}
            onNodesChange={onNodesChange}
            onEdgesChange={onEdgesChange}
            onConnect={onConnect}
            onDragOver={onDragOver}
            onDrop={onDrop}
            nodeTypes={nodeTypes}
            defaultEdgeOptions={defaultEdgeOptions}
            colorMode="light"
          >
            <Background color={theme.palette.primary.main} gap={16} />
            <Controls />
          </ReactFlow>
        </Box>
        <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
          <Button
            variant="contained"
            startIcon={<PlayArrowIcon />}
            onClick={handleCreateJob}
            disabled={!canCreate || createJob.isPending}
          >
            {createJob.isPending ? 'Creating...' : 'Create Job'}
          </Button>
        </Box>
      </Box>
    </Box>
  );
}
