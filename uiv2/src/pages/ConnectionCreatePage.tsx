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
import { useRegisterConnection } from '../hooks/useConnections';

interface FormState {
  name: string;
  databaseType: 'postgres' | 'mysql';
  hostname: string;
  port: string;
  database: string;
  username: string;
  password: string;
}

const initialFormState: FormState = {
  name: '',
  databaseType: 'postgres',
  hostname: 'postgres',
  port: '5432',
  database: 'demo',
  username: 'typestream',
  password: 'typestream',
};

export function ConnectionCreatePage() {
  const navigate = useNavigate();
  const registerConnection = useRegisterConnection();
  const [form, setForm] = useState<FormState>(initialFormState);
  const [error, setError] = useState<string | null>(null);

  const handleChange = (field: keyof FormState) => (
    e: React.ChangeEvent<HTMLInputElement>
  ) => {
    setForm((prev) => ({ ...prev, [field]: e.target.value }));

    // Update default port when database type changes
    if (field === 'databaseType') {
      const defaultPort = e.target.value === 'postgres' ? '5432' : '3306';
      setForm((prev) => ({ ...prev, databaseType: e.target.value as 'postgres' | 'mysql', port: defaultPort }));
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!form.name.trim()) {
      setError('Connection name is required');
      return;
    }

    // Validate name format (alphanumeric, dashes, underscores)
    if (!/^[a-zA-Z][a-zA-Z0-9_-]*$/.test(form.name)) {
      setError('Name must start with a letter and contain only letters, numbers, dashes, and underscores');
      return;
    }

    // Use the name as the ID for simplicity
    registerConnection.mutate({
      id: form.name,
      name: form.name,
      databaseType: form.databaseType,
      hostname: form.hostname,
      port: parseInt(form.port, 10),
      database: form.database,
      username: form.username,
      password: form.password,
    }, {
      onSuccess: (response) => {
        if (response.success) {
          navigate('/connections');
        } else {
          setError(response.error || 'Failed to register connection');
        }
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
          onClick={() => navigate('/connections')}
        >
          Back
        </Button>
        <Box>
          <Typography variant="h4">New Connection</Typography>
          <Typography variant="body2" color="text.secondary">
            Create a database connection to use as a sink target
          </Typography>
        </Box>
      </Box>

      <Paper sx={{ p: 3, maxWidth: 600 }}>
        <form onSubmit={handleSubmit}>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
            {(error || registerConnection.isError) && (
              <Alert severity="error">
                {error || registerConnection.error?.message}
              </Alert>
            )}

            <Typography variant="subtitle1" fontWeight="medium">
              Connection Identity
            </Typography>

            <TextField
              fullWidth
              label="Connection Name"
              value={form.name}
              onChange={handleChange('name')}
              placeholder="my-postgres-db"
              required
              helperText="This name will appear in the pipeline builder as a sink option"
            />

            <TextField
              fullWidth
              select
              label="Database Type"
              value={form.databaseType}
              onChange={handleChange('databaseType')}
            >
              <MenuItem value="postgres">PostgreSQL</MenuItem>
              <MenuItem value="mysql">MySQL</MenuItem>
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
                placeholder={form.databaseType === 'postgres' ? '5432' : '3306'}
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

            <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 2, mt: 2 }}>
              <Button
                variant="outlined"
                onClick={() => navigate('/connections')}
              >
                Cancel
              </Button>
              <Button
                type="submit"
                variant="contained"
                disabled={registerConnection.isPending}
                startIcon={registerConnection.isPending ? <CircularProgress size={20} /> : null}
              >
                {registerConnection.isPending ? 'Creating...' : 'Create Connection'}
              </Button>
            </Box>
          </Box>
        </form>
      </Paper>
    </Box>
  );
}
