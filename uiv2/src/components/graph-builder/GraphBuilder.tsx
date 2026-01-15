import { useCallback, useRef, useState, useEffect, useMemo } from 'react';
import {
  ReactFlow,
  Background,
  Controls,
  addEdge,
  useNodesState,
  useEdgesState,
  type OnConnect,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Alert from '@mui/material/Alert';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import { useNavigate } from 'react-router-dom';
import { NodePalette } from './NodePalette';
import { nodeTypes, type AppNode } from './nodes';
import { serializeGraph } from '../../utils/graphSerializer';
import { getGraphDependencyKey } from '../../utils/graphDependencyKey';
import { useCreateJob } from '../../hooks/useCreateJob';
import { useInferGraphSchemas } from '../../hooks/useInferGraphSchemas';
import { CreateJobFromGraphRequest, InferGraphSchemasRequest } from '../../generated/job_pb';

let nodeId = 0;
const getId = () => `node-${nodeId++}`;

export function GraphBuilder() {
  const navigate = useNavigate();
  const reactFlowWrapper = useRef<HTMLDivElement>(null);
  const [nodes, setNodes, onNodesChange] = useNodesState<AppNode>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState([]);
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
            return {
              ...n,
              data: {
                ...n.data,
                outputSchema: result?.fields ?? [],
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
    (params) => setEdges((eds) => addEdge(params, eds)),
    [setEdges]
  );

  const onDragOver = useCallback((event: React.DragEvent) => {
    event.preventDefault();
    event.dataTransfer.dropEffect = 'move';
  }, []);

  const onDrop = useCallback(
    (event: React.DragEvent) => {
      event.preventDefault();

      const type = event.dataTransfer.getData('application/reactflow');
      if (!type || !reactFlowWrapper.current) return;

      const bounds = reactFlowWrapper.current.getBoundingClientRect();
      const position = {
        x: event.clientX - bounds.left - 100,
        y: event.clientY - bounds.top - 50,
      };

      let data: Record<string, unknown>;
      if (type === 'kafkaSink') {
        data = { topicName: '' };
      } else if (type === 'geoIp') {
        data = { ipField: '', outputField: 'country_code' };
      } else if (type === 'inspector') {
        data = { label: '' };
      } else if (type === 'materializedView') {
        data = { aggregationType: 'count', groupByField: '' };
      } else if (type === 'textExtractor') {
        data = { filePathField: '', outputField: 'text' };
      } else if (type === 'embeddingGenerator') {
        data = { textField: '', outputField: 'embedding', model: 'text-embedding-3-small' };
      } else {
        data = { topicPath: '' };
      }

      const newNode: AppNode = {
        id: getId(),
        type,
        position,
        data,
      } as AppNode;

      setNodes((nds) => [...nds, newNode]);
    },
    [setNodes]
  );

  const handleCreateJob = () => {
    setCreateError(null);
    const graph = serializeGraph(nodes, edges);
    const request = new CreateJobFromGraphRequest({ userId: 'local', graph });

    createJob.mutate(request, {
      onSuccess: (response) => {
        if (response.success && response.jobId) {
          navigate(`/jobs/${response.jobId}`);
        } else if (response.error) {
          setCreateError(response.error);
        }
      },
    });
  };

  const canCreate = nodes.length > 0;

  return (
    <Box sx={{ display: 'flex', height: '100%', gap: 2 }}>
      <NodePalette />
      <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 2 }}>
        {(createJob.isError || createError) && (
          <Alert severity="error">
            {createJob.isError ? createJob.error.message : createError}
          </Alert>
        )}
        <Box
          ref={reactFlowWrapper}
          sx={{
            flex: 1,
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
            colorMode="dark"
            fitView
          >
            <Background />
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
