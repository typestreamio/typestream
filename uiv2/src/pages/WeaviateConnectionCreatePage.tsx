import { useState } from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import TextField from '@mui/material/TextField';
import MenuItem from '@mui/material/MenuItem';
import Typography from '@mui/material/Typography';
import Alert from '@mui/material/Alert';
import CircularProgress from '@mui/material/CircularProgress';
import { useNavigate } from 'react-router-dom';
import { useRegisterWeaviateConnection } from '../hooks/useConnections';

export function WeaviateConnectionCreatePage() {
  const navigate = useNavigate();
  const registerConnection = useRegisterWeaviateConnection();

  const [name, setName] = useState('');
  const [restUrl, setRestUrl] = useState('http://localhost:8090');
  const [grpcUrl, setGrpcUrl] = useState('localhost:50051');
  const [grpcSecured, setGrpcSecured] = useState(false);
  const [authScheme, setAuthScheme] = useState('NONE');
  const [apiKey, setApiKey] = useState('');
  const [connectorRestUrl, setConnectorRestUrl] = useState('http://weaviate:8080');
  const [connectorGrpcUrl, setConnectorGrpcUrl] = useState('weaviate:50051');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    const id = name.toLowerCase().replace(/[^a-z0-9]/g, '-');

    registerConnection.mutate(
      {
        id,
        name,
        restUrl,
        grpcUrl,
        grpcSecured,
        authScheme,
        apiKey: authScheme === 'API_KEY' ? apiKey : '',
        connectorRestUrl,
        connectorGrpcUrl,
      },
      {
        onSuccess: (response) => {
          if (response.success) {
            navigate('/connections/weaviate');
          }
        },
      }
    );
  };

  return (
    <Box>
      <Typography variant="h4" sx={{ mb: 3 }}>
        Add Weaviate Connection
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
            placeholder="my-weaviate"
            helperText="A friendly name for this connection"
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
            placeholder="http://localhost:8090"
            helperText="Weaviate REST API URL for server health checks"
          />

          <TextField
            fullWidth
            label="gRPC URL"
            value={grpcUrl}
            onChange={(e) => setGrpcUrl(e.target.value)}
            required
            sx={{ mb: 2 }}
            placeholder="localhost:50051"
            helperText="Weaviate gRPC URL (without protocol)"
          />

          <TextField
            fullWidth
            select
            label="gRPC Secured"
            value={grpcSecured ? 'true' : 'false'}
            onChange={(e) => setGrpcSecured(e.target.value === 'true')}
            sx={{ mb: 2 }}
          >
            <MenuItem value="false">No (plaintext)</MenuItem>
            <MenuItem value="true">Yes (TLS)</MenuItem>
          </TextField>

          <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 1, mt: 2 }}>
            Kafka Connect URLs (Docker network)
          </Typography>

          <TextField
            fullWidth
            label="Connector REST URL"
            value={connectorRestUrl}
            onChange={(e) => setConnectorRestUrl(e.target.value)}
            sx={{ mb: 2 }}
            placeholder="http://weaviate:8080"
            helperText="REST URL used by Kafka Connect (Docker hostname)"
          />

          <TextField
            fullWidth
            label="Connector gRPC URL"
            value={connectorGrpcUrl}
            onChange={(e) => setConnectorGrpcUrl(e.target.value)}
            sx={{ mb: 2 }}
            placeholder="weaviate:50051"
            helperText="gRPC URL used by Kafka Connect (Docker hostname)"
          />

          <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 1, mt: 2 }}>
            Authentication
          </Typography>

          <TextField
            fullWidth
            select
            label="Auth Scheme"
            value={authScheme}
            onChange={(e) => setAuthScheme(e.target.value)}
            sx={{ mb: 2 }}
          >
            <MenuItem value="NONE">None (Anonymous)</MenuItem>
            <MenuItem value="API_KEY">API Key</MenuItem>
          </TextField>

          {authScheme === 'API_KEY' && (
            <TextField
              fullWidth
              label="API Key"
              value={apiKey}
              onChange={(e) => setApiKey(e.target.value)}
              type="password"
              required
              sx={{ mb: 2 }}
              helperText="Weaviate Cloud API key (stored securely server-side)"
            />
          )}

          {registerConnection.isError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {registerConnection.error?.message || 'Failed to register connection'}
            </Alert>
          )}

          <Box sx={{ display: 'flex', gap: 2 }}>
            <Button
              type="submit"
              variant="contained"
              disabled={registerConnection.isPending || !name}
            >
              {registerConnection.isPending ? (
                <CircularProgress size={24} />
              ) : (
                'Create Connection'
              )}
            </Button>
            <Button variant="outlined" onClick={() => navigate('/connections/weaviate')}>
              Cancel
            </Button>
          </Box>
        </form>
      </Paper>
    </Box>
  );
}
