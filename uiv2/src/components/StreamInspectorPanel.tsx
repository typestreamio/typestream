import { useEffect, useRef, useCallback, useState } from 'react';
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
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import { useReactFlow } from '@xyflow/react';
import { Light as SyntaxHighlighter } from 'react-syntax-highlighter';
import json from 'react-syntax-highlighter/dist/esm/languages/hljs/json';
import { vs2015 } from 'react-syntax-highlighter/dist/esm/styles/hljs';
import { usePreviewJob } from '../hooks/usePreviewJob';
import { serializeGraph } from '../utils/graphSerializer';

SyntaxHighlighter.registerLanguage('json', json);

interface StreamInspectorPanelProps {
  open: boolean;
  onClose: () => void;
  nodeId: string;
}

// Attempt to parse and pretty-print JSON, return original if not valid JSON
function formatValue(value: string): { formatted: string; isJson: boolean } {
  try {
    const parsed = JSON.parse(value);
    return { formatted: JSON.stringify(parsed, null, 2), isJson: true };
  } catch {
    return { formatted: value, isJson: false };
  }
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
  // Track which message rows are expanded
  const [expandedRows, setExpandedRows] = useState<Set<number>>(new Set());

  const toggleRow = (index: number) => {
    setExpandedRows((prev) => {
      const next = new Set(prev);
      if (next.has(index)) {
        next.delete(index);
      } else {
        next.add(index);
      }
      return next;
    });
  };

  // Update ref in useEffect to avoid "Cannot update ref during render" warning
  useEffect(() => {
    stopPreviewRef.current = stopPreview;
  }, [stopPreview]);

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
  }, [messages.length]);

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
                <TableCell sx={{ width: 40 }}></TableCell>
                <TableCell sx={{ width: 100 }}>Time</TableCell>
                <TableCell sx={{ width: 150 }}>Key</TableCell>
                <TableCell>Value</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {messages.length === 0 && !isStreaming && !error && (
                <TableRow>
                  <TableCell colSpan={4} align="center">
                    <Typography variant="body2" color="text.secondary">
                      No messages yet
                    </Typography>
                  </TableCell>
                </TableRow>
              )}
              {messages.map((msg, idx) => {
                const isExpanded = expandedRows.has(idx);
                const { formatted, isJson } = formatValue(msg.value);
                return (
                  <TableRow
                    key={idx}
                    onClick={() => toggleRow(idx)}
                    sx={{
                      cursor: 'pointer',
                      '&:hover': { backgroundColor: 'action.hover' },
                      verticalAlign: 'top',
                    }}
                    data-testid={`message-row-${idx}`}
                  >
                    <TableCell sx={{ padding: '6px' }}>
                      <IconButton size="small" aria-label={isExpanded ? 'Collapse' : 'Expand'}>
                        {isExpanded ? <ExpandLessIcon fontSize="small" /> : <ExpandMoreIcon fontSize="small" />}
                      </IconButton>
                    </TableCell>
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
                        maxWidth: isExpanded ? 'none' : 300,
                        overflow: isExpanded ? 'visible' : 'hidden',
                        textOverflow: isExpanded ? 'clip' : 'ellipsis',
                        whiteSpace: isExpanded ? 'pre-wrap' : 'nowrap',
                        wordBreak: isExpanded ? 'break-word' : 'normal',
                      }}
                    >
                      {isJson ? (
                        <SyntaxHighlighter
                          language="json"
                          style={vs2015}
                          customStyle={{
                            margin: 0,
                            padding: isExpanded ? '8px' : '0',
                            borderRadius: isExpanded ? '4px' : '0',
                            fontSize: '0.75rem',
                            background: isExpanded ? undefined : 'transparent',
                            overflow: isExpanded ? 'visible' : 'hidden',
                            textOverflow: isExpanded ? 'clip' : 'ellipsis',
                            whiteSpace: isExpanded ? 'pre-wrap' : 'nowrap',
                          }}
                        >
                          {isExpanded ? formatted : msg.value}
                        </SyntaxHighlighter>
                      ) : isExpanded ? (
                        <Box
                          component="pre"
                          sx={{
                            margin: 0,
                            padding: '8px',
                            backgroundColor: 'grey.900',
                            color: 'grey.100',
                            borderRadius: '4px',
                            whiteSpace: 'pre-wrap',
                            wordBreak: 'break-word',
                          }}
                        >
                          {msg.value}
                        </Box>
                      ) : (
                        msg.value
                      )}
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </TableContainer>

        <Box
          sx={{
            p: 2,
            borderTop: '1px solid',
            borderColor: 'divider',
          }}
        >
          <Typography variant="body2" color="text.secondary">
            {messages.length} messages shown
          </Typography>
        </Box>
      </Box>
    </Drawer>
  );
}
