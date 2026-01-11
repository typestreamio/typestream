import { useCallback, useRef, useState } from 'react';
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
import { useCreateJob } from '../../hooks/useCreateJob';
import { CreateJobFromGraphRequest } from '../../generated/job_pb';

let nodeId = 0;
const getId = () => `node-${nodeId++}`;

export function GraphBuilder() {
  const navigate = useNavigate();
  const reactFlowWrapper = useRef<HTMLDivElement>(null);
  const [nodes, setNodes, onNodesChange] = useNodesState<AppNode>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState([]);
  const [createError, setCreateError] = useState<string | null>(null);
  const createJob = useCreateJob();

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
      } else if (type === 'inspector') {
        data = { label: '' };
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
