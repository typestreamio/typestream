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
  const prevConnectedRef = useRef(isConnected);
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Track reconnection: was disconnected, now connected
  // Show a brief "reconnected" notification when connection is restored
  useEffect(() => {
    const wasDisconnected = !prevConnectedRef.current;
    const justReconnected = wasDisconnected && isConnected;

    if (justReconnected) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- Legitimate: showing transient notification on reconnection
      setShowReconnected(true);
      reconnectTimerRef.current = setTimeout(() => {
        setShowReconnected(false);
      }, 3000);
    }

    // Update previous value for next comparison
    prevConnectedRef.current = isConnected;

    return () => {
      if (reconnectTimerRef.current) {
        clearTimeout(reconnectTimerRef.current);
      }
    };
  }, [isConnected]);

  // Update current time every second when disconnected (for duration display)
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!isConnected && disconnectedSince) {
      const interval = setInterval(() => setNow(Date.now()), 1000);
      return () => clearInterval(interval);
    }
  }, [isConnected, disconnectedSince]);

  const formatDuration = (since: Date | null) => {
    if (!since) return '';
    const seconds = Math.floor((now - since.getTime()) / 1000);
    if (seconds < 60) return `${seconds}s`;
    const minutes = Math.floor(seconds / 60);
    return `${minutes}m ${seconds % 60}s`;
  };

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
          onClose={() => setShowReconnected(false)}
        >
          Connection restored
        </Alert>
      </Collapse>
    </>
  );
}
