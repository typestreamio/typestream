import { useEffect, useRef, useCallback } from 'react';
import Drawer from '@mui/material/Drawer';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import IconButton from '@mui/material/IconButton';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import Alert from '@mui/material/Alert';
import CloseIcon from '@mui/icons-material/Close';
import NavigateBeforeIcon from '@mui/icons-material/NavigateBefore';
import NavigateNextIcon from '@mui/icons-material/NavigateNext';
import { useReactFlow } from '@xyflow/react';
import { Light as SyntaxHighlighter } from 'react-syntax-highlighter';
import json from 'react-syntax-highlighter/dist/esm/languages/hljs/json';
import { vs2015 } from 'react-syntax-highlighter/dist/esm/styles/hljs';
import { useSnapshotPreview } from '../hooks/useSnapshotPreview';
import { augmentGraphForPreview } from '../utils/graphPreviewHelper';

SyntaxHighlighter.registerLanguage('json', json);

interface NodeSnapshotPanelProps {
  open: boolean;
  onClose: () => void;
  nodeId: string;
  nodeTitle: string;
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

export function NodeSnapshotPanel({
  open,
  onClose,
  nodeId,
  nodeTitle,
}: NodeSnapshotPanelProps) {
  const { getNodes, getEdges } = useReactFlow();
  const {
    currentMessage,
    currentIndex,
    totalCount,
    isLoading,
    error,
    startSnapshot,
    stopSnapshot,
    goToNext,
    goToPrevious,
  } = useSnapshotPreview();

  const hasStartedRef = useRef(false);
  const stopSnapshotRef = useRef(stopSnapshot);

  // Update ref to avoid stale closure in cleanup
  useEffect(() => {
    stopSnapshotRef.current = stopSnapshot;
  }, [stopSnapshot]);

  const handleStart = useCallback(() => {
    const nodes = getNodes();
    const edges = getEdges();

    try {
      const { graph, inspectorNodeId } = augmentGraphForPreview(nodes, edges, nodeId);
      startSnapshot(graph, inspectorNodeId);
    } catch (e) {
      console.error('Failed to start preview:', e);
    }
  }, [getNodes, getEdges, nodeId, startSnapshot]);

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

  // Stop preview when component unmounts
  useEffect(() => {
    return () => {
      if (hasStartedRef.current) {
        stopSnapshotRef.current();
      }
    };
  }, []);

  const handleClose = () => {
    stopSnapshot();
    onClose();
  };

  const formatTimestamp = (ts: number) => {
    return new Date(ts).toLocaleTimeString();
  };

  const { formatted, isJson } = currentMessage
    ? formatValue(currentMessage.value)
    : { formatted: '', isJson: false };

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
        {/* Header */}
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
            <Typography variant="h6">Preview: {nodeTitle}</Typography>
            {isLoading && <CircularProgress size={20} />}
          </Box>
          <IconButton onClick={handleClose} size="small" aria-label="Close">
            <CloseIcon />
          </IconButton>
        </Box>

        {/* Error display */}
        {error && (
          <Alert severity="error" sx={{ m: 2 }}>
            {error}
          </Alert>
        )}

        {/* Message counter and timestamp */}
        {totalCount > 0 && (
          <Box
            sx={{
              px: 2,
              py: 1,
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              borderBottom: '1px solid',
              borderColor: 'divider',
              bgcolor: 'action.hover',
            }}
          >
            <Typography variant="body2" fontWeight="medium">
              Message {currentIndex + 1} of {totalCount}
            </Typography>
            {currentMessage && (
              <Typography variant="body2" color="text.secondary">
                {formatTimestamp(currentMessage.timestamp)}
              </Typography>
            )}
          </Box>
        )}

        {/* Content area */}
        <Box sx={{ flex: 1, overflow: 'auto', p: 2 }}>
          {isLoading && totalCount === 0 && (
            <Box
              sx={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                height: '100%',
                gap: 2,
              }}
            >
              <CircularProgress />
              <Typography color="text.secondary">
                Fetching messages...
              </Typography>
            </Box>
          )}

          {!isLoading && totalCount === 0 && !error && (
            <Box
              sx={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                height: '100%',
              }}
            >
              <Typography color="text.secondary">
                No messages available
              </Typography>
            </Box>
          )}

          {currentMessage && (
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
              {/* Key display */}
              <Box>
                <Typography
                  variant="caption"
                  color="text.secondary"
                  fontWeight="bold"
                  sx={{ textTransform: 'uppercase', letterSpacing: 0.5 }}
                >
                  Key
                </Typography>
                <Box
                  sx={{
                    mt: 0.5,
                    p: 1.5,
                    bgcolor: 'grey.900',
                    borderRadius: 1,
                    fontFamily: 'monospace',
                    fontSize: '0.875rem',
                  }}
                >
                  {currentMessage.key || <em style={{ opacity: 0.5 }}>null</em>}
                </Box>
              </Box>

              {/* Value display */}
              <Box sx={{ flex: 1 }}>
                <Typography
                  variant="caption"
                  color="text.secondary"
                  fontWeight="bold"
                  sx={{ textTransform: 'uppercase', letterSpacing: 0.5 }}
                >
                  Value
                </Typography>
                <Box
                  sx={{
                    mt: 0.5,
                    borderRadius: 1,
                    overflow: 'auto',
                  }}
                >
                  {isJson ? (
                    <SyntaxHighlighter
                      language="json"
                      style={vs2015}
                      customStyle={{
                        margin: 0,
                        padding: '12px',
                        borderRadius: '4px',
                        fontSize: '0.875rem',
                      }}
                    >
                      {formatted}
                    </SyntaxHighlighter>
                  ) : (
                    <Box
                      component="pre"
                      sx={{
                        margin: 0,
                        padding: '12px',
                        backgroundColor: 'grey.900',
                        borderRadius: '4px',
                        whiteSpace: 'pre-wrap',
                        wordBreak: 'break-word',
                        fontFamily: 'monospace',
                        fontSize: '0.875rem',
                      }}
                    >
                      {currentMessage.value}
                    </Box>
                  )}
                </Box>
              </Box>
            </Box>
          )}
        </Box>

        {/* Footer with navigation */}
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
          <Button
            variant="outlined"
            size="small"
            startIcon={<NavigateBeforeIcon />}
            onClick={goToPrevious}
            disabled={currentIndex === 0 || totalCount === 0}
          >
            Back
          </Button>
          <Typography variant="body2" color="text.secondary">
            {totalCount > 0
              ? `${totalCount} message${totalCount !== 1 ? 's' : ''} captured`
              : 'No messages'}
          </Typography>
          <Button
            variant="outlined"
            size="small"
            endIcon={<NavigateNextIcon />}
            onClick={goToNext}
            disabled={currentIndex >= totalCount - 1 || totalCount === 0}
          >
            Next
          </Button>
        </Box>
      </Box>
    </Drawer>
  );
}
