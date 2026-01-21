import { useState, useEffect, useRef } from 'react';
import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import Collapse from '@mui/material/Collapse';
import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';
import Button from '@mui/material/Button';
import RefreshIcon from '@mui/icons-material/Refresh';
import { useServerConnection } from '../../providers/ServerConnectionContext';

export function ServerStatusBanner() {
  const { isConnected, disconnectedSince } = useServerConnection();
  const [showReconnected, setShowReconnected] = useState(false);
  const [wasDisconnected, setWasDisconnected] = useState(false);
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Track reconnection for showing "Reconnected" message
  useEffect(() => {
    if (!isConnected) {
      setWasDisconnected(true);
    } else if (wasDisconnected && isConnected) {
      // Just reconnected - show success message briefly
      setShowReconnected(true);
      reconnectTimerRef.current = setTimeout(() => {
        setShowReconnected(false);
        setWasDisconnected(false);
      }, 3000);
    }

    return () => {
      if (reconnectTimerRef.current) {
        clearTimeout(reconnectTimerRef.current);
      }
    };
  }, [isConnected, wasDisconnected]);

  const formatDuration = (since: Date | null) => {
    if (!since) return '';
    const seconds = Math.floor((Date.now() - since.getTime()) / 1000);
    if (seconds < 60) return `${seconds}s`;
    const minutes = Math.floor(seconds / 60);
    return `${minutes}m ${seconds % 60}s`;
  };

  // Update duration every second when disconnected
  const [, setTick] = useState(0);
  useEffect(() => {
    if (!isConnected && disconnectedSince) {
      const interval = setInterval(() => setTick((t) => t + 1), 1000);
      return () => clearInterval(interval);
    }
  }, [isConnected, disconnectedSince]);

  return (
    <>
      {/* Disconnected Banner */}
      <Collapse in={!isConnected}>
        <Alert
          severity="warning"
          sx={{
            borderRadius: 0,
            '& .MuiAlert-message': { width: '100%' },
          }}
          action={
            <Button
              color="inherit"
              size="small"
              startIcon={<RefreshIcon />}
              onClick={() => window.location.reload()}
            >
              Reload
            </Button>
          }
        >
          <AlertTitle sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <CircularProgress size={16} color="inherit" />
            Server Disconnected
          </AlertTitle>
          <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
            <span>
              Unable to reach the TypeStream server. Attempting to reconnect...
            </span>
            {disconnectedSince && (
              <span style={{ opacity: 0.7 }}>{formatDuration(disconnectedSince)}</span>
            )}
          </Box>
        </Alert>
      </Collapse>

      {/* Reconnected Success Banner */}
      <Collapse in={showReconnected}>
        <Alert
          severity="success"
          sx={{ borderRadius: 0 }}
          onClose={() => {
            setShowReconnected(false);
            setWasDisconnected(false);
          }}
        >
          Connection restored
        </Alert>
      </Collapse>
    </>
  );
}
