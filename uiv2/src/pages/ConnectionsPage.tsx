import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import HelpOutlineIcon from '@mui/icons-material/HelpOutline';
import SyncIcon from '@mui/icons-material/Sync';
import CircularProgress from '@mui/material/CircularProgress';
import Chip from '@mui/material/Chip';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import { useNavigate } from 'react-router-dom';
import {
  useConnections,
  useUnregisterConnection,
  type Connection,
} from '../hooks/useConnections';

function ConnectionStatusChip({ state, error }: { state: Connection['state']; error?: string }) {
  switch (state) {
    case 'connected':
      return (
        <Chip
          icon={<CheckCircleIcon />}
          label="Connected"
          color="success"
          size="small"
          variant="outlined"
        />
      );
    case 'connecting':
      return (
        <Chip
          icon={<SyncIcon sx={{ animation: 'spin 1s linear infinite', '@keyframes spin': { from: { transform: 'rotate(0deg)' }, to: { transform: 'rotate(360deg)' } } }} />}
          label="Connecting"
          color="warning"
          size="small"
          variant="outlined"
        />
      );
    case 'disconnected':
    case 'error':
      return (
        <Tooltip title={error || 'Connection failed'}>
          <Chip
            icon={<ErrorIcon />}
            label={state === 'error' ? 'Error' : 'Disconnected'}
            color="error"
            size="small"
            variant="outlined"
          />
        </Tooltip>
      );
    default:
      return (
        <Chip
          icon={<HelpOutlineIcon />}
          label="Unknown"
          color="default"
          size="small"
          variant="outlined"
        />
      );
  }
}

function DatabaseTypeChip({ type }: { type: string }) {
  return (
    <Chip
      label={type.toUpperCase()}
      size="small"
      color={type === 'postgres' ? 'primary' : 'secondary'}
    />
  );
}

export function ConnectionsPage() {
  const navigate = useNavigate();
  const { data: connections, isLoading } = useConnections();
  const unregisterConnection = useUnregisterConnection();

  const handleDelete = (id: string, name: string) => {
    if (confirm(`Delete connection "${name}"?`)) {
      unregisterConnection.mutate(id);
    }
  };

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Box>
          <Typography variant="h4">Connections</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            Database connections available as sink targets in pipelines
          </Typography>
        </Box>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => navigate('/connections/new')}
        >
          New Connection
        </Button>
      </Box>

      {isLoading && (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
          <CircularProgress />
        </Box>
      )}

      {!isLoading && (!connections || connections.length === 0) && (
        <Paper sx={{ p: 3, textAlign: 'center' }}>
          <Typography color="text.secondary" sx={{ mb: 2 }}>
            No connections configured. Create a connection to use as a sink in your pipelines.
          </Typography>
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={() => navigate('/connections/new')}
          >
            Create Your First Connection
          </Button>
        </Paper>
      )}

      {!isLoading && connections && connections.length > 0 && (
        <TableContainer component={Paper}>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Name</TableCell>
                <TableCell>Type</TableCell>
                <TableCell>Host</TableCell>
                <TableCell>Database</TableCell>
                <TableCell>Status</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {connections.map((connection) => (
                <TableRow key={connection.id} hover>
                  <TableCell sx={{ fontFamily: 'monospace', fontWeight: 'medium' }}>
                    {connection.name}
                  </TableCell>
                  <TableCell>
                    <DatabaseTypeChip type={connection.databaseType} />
                  </TableCell>
                  <TableCell sx={{ fontFamily: 'monospace' }}>
                    {connection.hostname}:{connection.port}
                  </TableCell>
                  <TableCell sx={{ fontFamily: 'monospace' }}>
                    {connection.database}
                  </TableCell>
                  <TableCell>
                    <ConnectionStatusChip state={connection.state} error={connection.error} />
                  </TableCell>
                  <TableCell align="right">
                    <Tooltip title="Delete">
                      <IconButton
                        size="small"
                        color="error"
                        onClick={() => handleDelete(connection.id, connection.name)}
                        disabled={unregisterConnection.isPending}
                      >
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </Box>
  );
}
