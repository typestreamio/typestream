import { useEffect, useRef, useCallback } from 'react';
import Drawer from '@mui/material/Drawer';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import IconButton from '@mui/material/IconButton';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Paper from '@mui/material/Paper';
import CircularProgress from '@mui/material/CircularProgress';
import Alert from '@mui/material/Alert';
import CloseIcon from '@mui/icons-material/Close';
import { useReactFlow } from '@xyflow/react';
import { usePreviewJob } from '../hooks/usePreviewJob';
import { serializeGraph } from '../utils/graphSerializer';

interface StreamInspectorPanelProps {
  open: boolean;
  onClose: () => void;
  nodeId: string;
}

export function StreamInspectorPanel({
  open,
  onClose,
  nodeId,
}: StreamInspectorPanelProps) {
  const { getNodes, getEdges } = useReactFlow();
  const { messages, isStreaming, error, startPreview, stopPreview } =
    usePreviewJob();
  const tableContainerRef = useRef<HTMLDivElement>(null);
  const hasStartedRef = useRef(false);
  // Use ref to avoid cleanup effect re-running when stopPreview changes
  const stopPreviewRef = useRef(stopPreview);
  stopPreviewRef.current = stopPreview;

  const handleStart = useCallback(() => {
    const nodes = getNodes();
    const edges = getEdges();
    const graph = serializeGraph(nodes, edges);
    startPreview(graph, nodeId);
  }, [getNodes, getEdges, nodeId, startPreview]);

  // Start preview when panel opens
  useEffect(() => {
    if (open && !hasStartedRef.current) {
      hasStartedRef.current = true;
      handleStart();
    }
    if (!open) {
      hasStartedRef.current = false;
    }
  }, [open, handleStart]);

  // Stop preview when component unmounts (empty deps = only on unmount)
  useEffect(() => {
    return () => {
      if (hasStartedRef.current) {
        stopPreviewRef.current();
      }
    };
  }, []);

  // Auto-scroll to bottom when new messages arrive
  useEffect(() => {
    if (tableContainerRef.current) {
      tableContainerRef.current.scrollTop =
        tableContainerRef.current.scrollHeight;
    }
  }, [messages]);

  const handleClose = () => {
    stopPreview();
    onClose();
  };

  const formatTimestamp = (ts: number) => {
    return new Date(ts).toLocaleTimeString();
  };

  return (
    <Drawer
      anchor="right"
      open={open}
      onClose={handleClose}
      PaperProps={{
        sx: { width: 600 },
      }}
    >
      <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
        <Box
          sx={{
            p: 2,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            borderBottom: '1px solid',
            borderColor: 'divider',
          }}
        >
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Typography variant="h6">Stream Inspector</Typography>
            {isStreaming && <CircularProgress size={20} />}
          </Box>
          <IconButton onClick={handleClose} size="small" aria-label="Close">
            <CloseIcon />
          </IconButton>
        </Box>

        {error && (
          <Alert severity="error" sx={{ m: 2 }}>
            {error}
          </Alert>
        )}

        <TableContainer
          component={Paper}
          ref={tableContainerRef}
          sx={{ flex: 1, overflow: 'auto', m: 2, mt: 0 }}
        >
          <Table stickyHeader size="small">
            <TableHead>
              <TableRow>
                <TableCell sx={{ width: 100 }}>Time</TableCell>
                <TableCell sx={{ width: 150 }}>Key</TableCell>
                <TableCell>Value</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {messages.length === 0 && !isStreaming && !error && (
                <TableRow>
                  <TableCell colSpan={3} align="center">
                    <Typography variant="body2" color="text.secondary">
                      No messages yet
                    </Typography>
                  </TableCell>
                </TableRow>
              )}
              {messages.map((msg, idx) => (
                <TableRow key={idx}>
                  <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.75rem' }}>
                    {formatTimestamp(msg.timestamp)}
                  </TableCell>
                  <TableCell
                    sx={{
                      fontFamily: 'monospace',
                      fontSize: '0.75rem',
                      maxWidth: 150,
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      whiteSpace: 'nowrap',
                    }}
                  >
                    {msg.key || '-'}
                  </TableCell>
                  <TableCell
                    sx={{
                      fontFamily: 'monospace',
                      fontSize: '0.75rem',
                      maxWidth: 300,
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      whiteSpace: 'nowrap',
                    }}
                  >
                    {msg.value}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>

        <Box
          sx={{
            p: 2,
            borderTop: '1px solid',
            borderColor: 'divider',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          <Typography variant="body2" color="text.secondary">
            {messages.length} messages (last 100 shown)
          </Typography>
        </Box>
      </Box>
    </Drawer>
  );
}
