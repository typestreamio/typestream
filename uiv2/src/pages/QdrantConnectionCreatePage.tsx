import { useState, useMemo } from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import Alert from '@mui/material/Alert';
import CircularProgress from '@mui/material/CircularProgress';
import { useNavigate } from 'react-router-dom';
import { useRegisterQdrantConnection } from '../hooks/useConnections';

// Simple URL validation helper
function isValidUrl(url: string): boolean {
  try {
    new URL(url);
    return true;
  } catch {
    return false;
  }
}

export function QdrantConnectionCreatePage() {
  const navigate = useNavigate();
  const registerConnection = useRegisterQdrantConnection();

  const [name, setName] = useState('');
  const [restUrl, setRestUrl] = useState('http://localhost:6333');
  const [grpcUrl, setGrpcUrl] = useState('http://localhost:6334');
  const [apiKey, setApiKey] = useState('');
  const [connectorGrpcUrl, setConnectorGrpcUrl] = useState('http://qdrant:6334');

  // Form validation
  const validation = useMemo(() => {
    const errors: Record<string, string> = {};

    if (!name.trim()) {
      errors.name = 'Connection name is required';
    }

    if (!restUrl.trim()) {
      errors.restUrl = 'REST URL is required';
    } else if (!isValidUrl(restUrl)) {
      errors.restUrl = 'Invalid URL format (must include protocol, e.g., http://)';
    }

    if (!grpcUrl.trim()) {
      errors.grpcUrl = 'gRPC URL is required';
    } else if (!isValidUrl(grpcUrl)) {
      errors.grpcUrl = 'Invalid URL format (must include protocol, e.g., http://)';
    }

    if (connectorGrpcUrl && !isValidUrl(connectorGrpcUrl)) {
      errors.connectorGrpcUrl = 'Invalid URL format';
    }

    return {
      errors,
      isValid: Object.keys(errors).length === 0,
    };
  }, [name, restUrl, grpcUrl, connectorGrpcUrl]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!validation.isValid) {
      return;
    }

    const id = name.toLowerCase().replace(/[^a-z0-9]/g, '-');

    registerConnection.mutate(
      {
        id,
        name,
        restUrl,
        grpcUrl,
        apiKey,
        connectorGrpcUrl,
      },
      {
        onSuccess: (response) => {
          if (response.success) {
            navigate('/connections/qdrant');
          }
        },
      }
    );
  };

  return (
    <Box>
      <Typography variant="h4" sx={{ mb: 3 }}>
        Add Qdrant Connection
      </Typography>

      <Paper sx={{ p: 3, maxWidth: 600 }}>
        <form onSubmit={handleSubmit}>
          <TextField
            fullWidth
            label="Connection Name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
            sx={{ mb: 2 }}
            placeholder="my-qdrant"
            helperText={validation.errors.name || "A friendly name for this connection"}
            error={!!validation.errors.name}
          />

          <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 1, mt: 2 }}>
            Server Connection (for health checks)
          </Typography>

          <TextField
            fullWidth
            label="REST URL"
            value={restUrl}
            onChange={(e) => setRestUrl(e.target.value)}
            required
            sx={{ mb: 2 }}
            placeholder="http://localhost:6333"
            helperText={validation.errors.restUrl || "Qdrant REST API URL for server health checks"}
            error={!!validation.errors.restUrl}
          />

          <TextField
            fullWidth
            label="gRPC URL"
            value={grpcUrl}
            onChange={(e) => setGrpcUrl(e.target.value)}
            required
            sx={{ mb: 2 }}
            placeholder="http://localhost:6334"
            helperText={validation.errors.grpcUrl || "Qdrant gRPC URL (port 6334, with protocol)"}
            error={!!validation.errors.grpcUrl}
          />

          <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 1, mt: 2 }}>
            Kafka Connect URL (Docker network)
          </Typography>

          <TextField
            fullWidth
            label="Connector gRPC URL"
            value={connectorGrpcUrl}
            onChange={(e) => setConnectorGrpcUrl(e.target.value)}
            sx={{ mb: 2 }}
            placeholder="http://qdrant:6334"
            helperText={validation.errors.connectorGrpcUrl || "gRPC URL used by Kafka Connect (Docker hostname)"}
            error={!!validation.errors.connectorGrpcUrl}
          />

          <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 1, mt: 2 }}>
            Authentication
          </Typography>

          <TextField
            fullWidth
            label="API Key (optional)"
            value={apiKey}
            onChange={(e) => setApiKey(e.target.value)}
            type="password"
            sx={{ mb: 2 }}
            helperText="Qdrant Cloud API key (stored securely server-side)"
          />

          {registerConnection.isError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {registerConnection.error?.message || 'Failed to register connection'}
            </Alert>
          )}

          <Box sx={{ display: 'flex', gap: 2 }}>
            <Button
              type="submit"
              variant="contained"
              disabled={registerConnection.isPending || !validation.isValid}
            >
              {registerConnection.isPending ? (
                <CircularProgress size={24} />
              ) : (
                'Create Connection'
              )}
            </Button>
            <Button variant="outlined" onClick={() => navigate('/connections/qdrant')}>
              Cancel
            </Button>
          </Box>
        </form>
      </Paper>
    </Box>
  );
}
