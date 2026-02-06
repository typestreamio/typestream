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
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import HelpOutlineIcon from '@mui/icons-material/HelpOutline';
import SyncIcon from '@mui/icons-material/Sync';
import CircularProgress from '@mui/material/CircularProgress';
import Chip from '@mui/material/Chip';
import { useNavigate } from 'react-router-dom';
import {
  useElasticsearchConnections,
  type ElasticsearchConnection,
} from '../hooks/useConnections';

function ConnectionStatusChip({ state, error }: { state: ElasticsearchConnection['state']; error?: string }) {
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
        <Chip
          icon={<ErrorIcon />}
          label={state === 'error' ? 'Error' : 'Disconnected'}
          color="error"
          size="small"
          variant="outlined"
          title={error || 'Connection failed'}
        />
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

export function ElasticsearchConnectionsPage() {
  const navigate = useNavigate();
  const { data: connections, isLoading } = useElasticsearchConnections();

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Box>
          <Typography variant="h4">Elasticsearch Connections</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            Elasticsearch connections available as sink targets in pipelines
          </Typography>
        </Box>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => navigate('/connections/elasticsearch/new')}
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
            No Elasticsearch connections configured. Create a connection to use as a search engine sink in your pipelines.
          </Typography>
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={() => navigate('/connections/elasticsearch/new')}
          >
            Create Your First Elasticsearch Connection
          </Button>
        </Paper>
      )}

      {!isLoading && connections && connections.length > 0 && (
        <TableContainer component={Paper}>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Name</TableCell>
                <TableCell>Connection URL</TableCell>
                <TableCell>Username</TableCell>
                <TableCell>Status</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {connections.map((connection) => (
                <TableRow key={connection.id} hover>
                  <TableCell sx={{ fontFamily: 'monospace', fontWeight: 'medium' }}>
                    {connection.name}
                  </TableCell>
                  <TableCell sx={{ fontFamily: 'monospace' }}>
                    {connection.connectionUrl}
                  </TableCell>
                  <TableCell>
                    {connection.username || <Typography color="text.secondary" component="span">-</Typography>}
                  </TableCell>
                  <TableCell>
                    <ConnectionStatusChip state={connection.state} error={connection.error} />
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
