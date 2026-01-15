import { useState } from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import MenuItem from '@mui/material/MenuItem';
import Alert from '@mui/material/Alert';
import CircularProgress from '@mui/material/CircularProgress';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import { useNavigate } from 'react-router-dom';
import { useCreateConnector } from '../hooks/useConnectors';
import { connectApi } from '../services/connectApi';

type DatabaseType = 'postgres';

interface FormState {
  connectorName: string;
  databaseType: DatabaseType;
  hostname: string;
  port: string;
  database: string;
  username: string;
  password: string;
  topicPrefix: string;
  tableFilter: string;
  schemaFilter: string;
}

const initialFormState: FormState = {
  connectorName: '',
  databaseType: 'postgres',
  hostname: 'postgres',
  port: '5432',
  database: 'demo',
  username: 'typestream',
  password: 'typestream',
  topicPrefix: '',
  tableFilter: '',
  schemaFilter: 'public',
};

export function ConnectorCreatePage() {
  const navigate = useNavigate();
  const createConnector = useCreateConnector();
  const [form, setForm] = useState<FormState>(initialFormState);
  const [error, setError] = useState<string | null>(null);

  const handleChange = (field: keyof FormState) => (
    e: React.ChangeEvent<HTMLInputElement>
  ) => {
    setForm((prev) => ({ ...prev, [field]: e.target.value }));
    // Auto-generate topic prefix from connector name if not set
    if (field === 'connectorName' && !form.topicPrefix) {
      setForm((prev) => ({
        ...prev,
        connectorName: e.target.value,
        topicPrefix: e.target.value.toLowerCase().replace(/[^a-z0-9]/g, '-'),
      }));
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!form.connectorName.trim()) {
      setError('Connector name is required');
      return;
    }

    const request = connectApi.buildPostgresSourceConfig({
      name: form.connectorName,
      hostname: form.hostname,
      port: form.port,
      database: form.database,
      username: form.username,
      password: form.password,
      topicPrefix: form.topicPrefix || form.connectorName,
      tableFilter: form.tableFilter || undefined,
      schemaFilter: form.schemaFilter || undefined,
    });

    createConnector.mutate(request, {
      onSuccess: () => {
        navigate('/connectors');
      },
      onError: (err) => {
        setError(err.message);
      },
    });
  };

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 3 }}>
        <Button
          startIcon={<ArrowBackIcon />}
          onClick={() => navigate('/connectors')}
        >
          Back
        </Button>
        <Typography variant="h4">Create Connector</Typography>
      </Box>

      <Paper sx={{ p: 3, maxWidth: 600 }}>
        <form onSubmit={handleSubmit}>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
            {(error || createConnector.isError) && (
              <Alert severity="error">
                {error || createConnector.error?.message}
              </Alert>
            )}

            <Typography variant="subtitle1" fontWeight="medium">
              Connector Settings
            </Typography>

            <TextField
              fullWidth
              label="Connector Name"
              value={form.connectorName}
              onChange={handleChange('connectorName')}
              placeholder="my-postgres-connector"
              required
              helperText="Unique identifier for this connector"
            />

            <TextField
              fullWidth
              select
              label="Database Type"
              value={form.databaseType}
              onChange={handleChange('databaseType')}
            >
              <MenuItem value="postgres">PostgreSQL</MenuItem>
            </TextField>

            <Typography variant="subtitle1" fontWeight="medium" sx={{ mt: 1 }}>
              Connection Details
            </Typography>

            <Box sx={{ display: 'flex', gap: 2 }}>
              <TextField
                fullWidth
                label="Hostname"
                value={form.hostname}
                onChange={handleChange('hostname')}
                placeholder="localhost"
                required
              />
              <TextField
                label="Port"
                value={form.port}
                onChange={handleChange('port')}
                placeholder="5432"
                sx={{ width: 120 }}
                required
              />
            </Box>

            <TextField
              fullWidth
              label="Database"
              value={form.database}
              onChange={handleChange('database')}
              placeholder="mydb"
              required
            />

            <Box sx={{ display: 'flex', gap: 2 }}>
              <TextField
                fullWidth
                label="Username"
                value={form.username}
                onChange={handleChange('username')}
                required
              />
              <TextField
                fullWidth
                label="Password"
                type="password"
                value={form.password}
                onChange={handleChange('password')}
                required
              />
            </Box>

            <Typography variant="subtitle1" fontWeight="medium" sx={{ mt: 1 }}>
              CDC Configuration
            </Typography>

            <TextField
              fullWidth
              label="Topic Prefix"
              value={form.topicPrefix}
              onChange={handleChange('topicPrefix')}
              placeholder="dbserver"
              helperText="Topics created as {prefix}.{schema}.{table}"
            />

            <TextField
              fullWidth
              label="Schema Filter"
              value={form.schemaFilter}
              onChange={handleChange('schemaFilter')}
              placeholder="public"
              helperText="Comma-separated list of schemas to include (e.g., public)"
            />

            <TextField
              fullWidth
              label="Table Filter"
              value={form.tableFilter}
              onChange={handleChange('tableFilter')}
              placeholder="public.users,public.orders"
              helperText="Comma-separated list of tables (e.g., public.users,public.orders). Leave empty for all tables."
            />

            <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 2, mt: 2 }}>
              <Button
                variant="outlined"
                onClick={() => navigate('/connectors')}
              >
                Cancel
              </Button>
              <Button
                type="submit"
                variant="contained"
                disabled={createConnector.isPending}
                startIcon={createConnector.isPending ? <CircularProgress size={20} /> : null}
              >
                {createConnector.isPending ? 'Creating...' : 'Create Connector'}
              </Button>
            </Box>
          </Box>
        </form>
      </Paper>
    </Box>
  );
}
