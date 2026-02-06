import { useState, useMemo } from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import Alert from '@mui/material/Alert';
import CircularProgress from '@mui/material/CircularProgress';
import { useNavigate } from 'react-router-dom';
import { useRegisterElasticsearchConnection } from '../hooks/useConnections';

// Simple URL validation helper
function isValidUrl(url: string): boolean {
  try {
    new URL(url);
    return true;
  } catch {
    return false;
  }
}

export function ElasticsearchConnectionCreatePage() {
  const navigate = useNavigate();
  const registerConnection = useRegisterElasticsearchConnection();

  const [name, setName] = useState('');
  const [connectionUrl, setConnectionUrl] = useState('http://localhost:9200');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [connectorUrl, setConnectorUrl] = useState('http://elasticsearch:9200');

  // Form validation
  const validation = useMemo(() => {
    const errors: Record<string, string> = {};

    if (!name.trim()) {
      errors.name = 'Connection name is required';
    }

    if (!connectionUrl.trim()) {
      errors.connectionUrl = 'Connection URL is required';
    } else if (!isValidUrl(connectionUrl)) {
      errors.connectionUrl = 'Invalid URL format (must include protocol, e.g., http://)';
    }

    if (connectorUrl && !isValidUrl(connectorUrl)) {
      errors.connectorUrl = 'Invalid URL format';
    }

    return {
      errors,
      isValid: Object.keys(errors).length === 0,
    };
  }, [name, connectionUrl, connectorUrl]);

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
        connectionUrl,
        username,
        password,
        connectorUrl,
      },
      {
        onSuccess: (response) => {
          if (response.success) {
            navigate('/connections/elasticsearch');
          }
        },
      }
    );
  };

  return (
    <Box>
      <Typography variant="h4" sx={{ mb: 3 }}>
        Add Elasticsearch Connection
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
            placeholder="my-elasticsearch"
            helperText={validation.errors.name || "A friendly name for this connection"}
            error={!!validation.errors.name}
          />

          <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 1, mt: 2 }}>
            Server Connection (for health checks)
          </Typography>

          <TextField
            fullWidth
            label="Connection URL"
            value={connectionUrl}
            onChange={(e) => setConnectionUrl(e.target.value)}
            required
            sx={{ mb: 2 }}
            placeholder="http://localhost:9200"
            helperText={validation.errors.connectionUrl || "Elasticsearch URL for server health checks"}
            error={!!validation.errors.connectionUrl}
          />

          <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 1, mt: 2 }}>
            Kafka Connect URL (Docker network)
          </Typography>

          <TextField
            fullWidth
            label="Connector URL"
            value={connectorUrl}
            onChange={(e) => setConnectorUrl(e.target.value)}
            sx={{ mb: 2 }}
            placeholder="http://elasticsearch:9200"
            helperText={validation.errors.connectorUrl || "URL used by Kafka Connect (Docker hostname)"}
            error={!!validation.errors.connectorUrl}
          />

          <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 1, mt: 2 }}>
            Authentication (optional)
          </Typography>

          <TextField
            fullWidth
            label="Username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            sx={{ mb: 2 }}
            placeholder="elastic"
            helperText="Optional: for basic authentication"
          />

          <TextField
            fullWidth
            label="Password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            type="password"
            sx={{ mb: 2 }}
            helperText="Optional: for basic authentication (stored securely server-side)"
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
            <Button variant="outlined" onClick={() => navigate('/connections/elasticsearch')}>
              Cancel
            </Button>
          </Box>
        </form>
      </Paper>
    </Box>
  );
}
