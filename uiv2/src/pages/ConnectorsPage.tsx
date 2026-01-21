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
import RefreshIcon from '@mui/icons-material/Refresh';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import RestartAltIcon from '@mui/icons-material/RestartAlt';
import CircularProgress from '@mui/material/CircularProgress';
import Alert from '@mui/material/Alert';
import Chip from '@mui/material/Chip';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import { useNavigate } from 'react-router-dom';
import { useConnectors, useDeleteConnector, useRestartConnector } from '../hooks/useConnectors';
import { useServerConnection } from '../providers/ServerConnectionContext';
import type { ConnectorWithStatus } from '../hooks/useConnectors';

function ConnectorStatusChip({ connector }: { connector: ConnectorWithStatus }) {
  if (connector.error) {
    return <Chip label="ERROR" color="error" size="small" />;
  }
  if (!connector.status) {
    return <Chip label="UNKNOWN" color="default" size="small" />;
  }

  const state = connector.status.connector.state;
  const colorMap: Record<string, 'success' | 'warning' | 'error' | 'default'> = {
    RUNNING: 'success',
    PAUSED: 'warning',
    FAILED: 'error',
    UNASSIGNED: 'default',
  };

  return <Chip label={state} color={colorMap[state] || 'default'} size="small" />;
}

function ConnectorTypeChip({ type }: { type?: string }) {
  if (!type) return null;
  return (
    <Chip
      label={type.toUpperCase()}
      size="small"
      variant="outlined"
      color={type === 'source' ? 'primary' : 'secondary'}
    />
  );
}

export function ConnectorsPage() {
  const navigate = useNavigate();
  const { data: connectors, isLoading, error, refetch } = useConnectors();
  const deleteConnector = useDeleteConnector();
  const restartConnector = useRestartConnector();
  const { isConnected } = useServerConnection();

  // Don't show errors when server is disconnected (banner handles it)
  const showError = error && isConnected;

  const handleDelete = (name: string) => {
    if (confirm(`Delete connector "${name}"?`)) {
      deleteConnector.mutate(name);
    }
  };

  const handleRestart = (name: string) => {
    restartConnector.mutate(name);
  };

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="h4">Connectors</Typography>
        <Box sx={{ display: 'flex', gap: 1 }}>
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={() => navigate('/connectors/new')}
          >
            New Connector
          </Button>
          <Button
            variant="outlined"
            startIcon={<RefreshIcon />}
            onClick={() => refetch()}
          >
            Refresh
          </Button>
        </Box>
      </Box>

      {isLoading && (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
          <CircularProgress />
        </Box>
      )}

      {showError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          Error loading connectors. Please try again.
        </Alert>
      )}

      {!isLoading && !error && connectors?.length === 0 && (
        <Paper sx={{ p: 3, textAlign: 'center' }}>
          <Typography color="text.secondary" sx={{ mb: 2 }}>
            No connectors found. Create a connector to start streaming data from a database.
          </Typography>
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={() => navigate('/connectors/new')}
          >
            Create Your First Connector
          </Button>
        </Paper>
      )}

      {!isLoading && connectors && connectors.length > 0 && (
        <TableContainer component={Paper}>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Name</TableCell>
                <TableCell>Type</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Tasks</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {connectors.map((connector) => (
                <TableRow key={connector.name} hover>
                  <TableCell sx={{ fontFamily: 'monospace' }}>
                    {connector.name}
                  </TableCell>
                  <TableCell>
                    <ConnectorTypeChip type={connector.status?.type} />
                  </TableCell>
                  <TableCell>
                    <ConnectorStatusChip connector={connector} />
                  </TableCell>
                  <TableCell>
                    {connector.status?.tasks.length ?? '-'}
                  </TableCell>
                  <TableCell align="right">
                    <Tooltip title="Restart">
                      <IconButton
                        size="small"
                        onClick={() => handleRestart(connector.name)}
                        disabled={restartConnector.isPending}
                      >
                        <RestartAltIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="Delete">
                      <IconButton
                        size="small"
                        color="error"
                        onClick={() => handleDelete(connector.name)}
                        disabled={deleteConnector.isPending}
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
