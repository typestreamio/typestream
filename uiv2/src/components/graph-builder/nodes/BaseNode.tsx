import { memo, useCallback, useState } from 'react';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';
import IconButton from '@mui/material/IconButton';
import CircularProgress from '@mui/material/CircularProgress';
import Tooltip from '@mui/material/Tooltip';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import VisibilityIcon from '@mui/icons-material/Visibility';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import { useReactFlow } from '@xyflow/react';
import type { ReactNode } from 'react';
import type { SchemaField } from './index';
import { NodeSnapshotPanel } from '../../NodeSnapshotPanel';

interface BaseNodeProps {
  nodeId: string;
  title: string;
  icon?: ReactNode;
  error?: string;
  isInferring?: boolean;
  outputSchema?: SchemaField[];
  children: ReactNode;
}

export const BaseNode = memo(function BaseNode({ nodeId, title, icon, error, isInferring, outputSchema, children }: BaseNodeProps) {
  const { deleteElements } = useReactFlow();
  const [previewPanelOpen, setPreviewPanelOpen] = useState(false);

  const handleDelete = useCallback(() => {
    deleteElements({ nodes: [{ id: nodeId }] });
  }, [deleteElements, nodeId]);

  return (
    <>
    <Paper
      elevation={3}
      sx={{
        minWidth: 220,
        bgcolor: 'background.paper',
        border: error ? '2px solid' : '1px solid',
        borderColor: error ? 'error.main' : 'divider',
        opacity: isInferring ? 0.7 : 1,
        transition: 'border-color 0.2s, opacity 0.2s',
      }}
    >
      <Box
        sx={{
          px: 1.5,
          py: 1,
          borderBottom: '1px solid',
          borderColor: 'divider',
          bgcolor: 'action.hover',
          display: 'flex',
          alignItems: 'center',
          gap: 1,
        }}
      >
        {icon}
        <Typography variant="subtitle2" fontWeight="bold" sx={{ flex: 1 }}>
          {title}
        </Typography>
        {isInferring && <CircularProgress size={14} />}
        {outputSchema && outputSchema.length > 0 && (
          <Tooltip title="Preview messages" arrow>
            <IconButton
              size="small"
              onClick={() => setPreviewPanelOpen(true)}
              className="nodrag"
              sx={{
                p: 0.25,
                opacity: 0.6,
                '&:hover': { opacity: 1 },
              }}
            >
              <VisibilityIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        )}
        {error && (
          <Tooltip title={error} arrow>
            <ErrorOutlineIcon color="error" fontSize="small" />
          </Tooltip>
        )}
        <Tooltip title="Delete node">
          <IconButton
            size="small"
            onClick={handleDelete}
            className="nodrag"
            sx={{
              p: 0.25,
              opacity: 0.5,
              '&:hover': { opacity: 1, color: 'error.main' },
            }}
          >
            <DeleteOutlineIcon fontSize="small" />
          </IconButton>
        </Tooltip>
      </Box>
      <Box sx={{ p: 1.5 }}>{children}</Box>
    </Paper>

    <NodeSnapshotPanel
      open={previewPanelOpen}
      onClose={() => setPreviewPanelOpen(false)}
      nodeId={nodeId}
      nodeTitle={title}
      outputSchema={outputSchema}
    />
    </>
  );
});
