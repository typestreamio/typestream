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
import CircularProgress from '@mui/material/CircularProgress';
import Alert from '@mui/material/Alert';
import Chip from '@mui/material/Chip';
import { useNavigate } from 'react-router-dom';
import { useWatchJobs } from '../hooks/useWatchJobs';
import { JobStatusChip } from '../components/JobStatusChip';

export function JobsPage() {
  const navigate = useNavigate();
  const { jobs, isLoading, error, isConnected, reconnect } = useWatchJobs('local');

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
          <Typography variant="h4">Jobs</Typography>
          <Chip
            size="small"
            label={isConnected ? 'Live' : 'Disconnected'}
            color={isConnected ? 'success' : 'default'}
            variant="outlined"
          />
        </Box>
        <Box sx={{ display: 'flex', gap: 1 }}>
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={() => navigate('/jobs/new')}
          >
            Create Job
          </Button>
          <Button
            variant="outlined"
            startIcon={<RefreshIcon />}
            onClick={reconnect}
          >
            Reconnect
          </Button>
        </Box>
      </Box>

      {isLoading && (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
          <CircularProgress />
        </Box>
      )}

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          Error loading jobs: {error.message}
        </Alert>
      )}

      {!isLoading && !error && jobs.length === 0 && (
        <Paper sx={{ p: 3, textAlign: 'center' }}>
          <Typography color="text.secondary">
            No jobs found. Submit a pipeline to create a job.
          </Typography>
        </Paper>
      )}

      {!isLoading && jobs.length > 0 && (
        <TableContainer component={Paper}>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Job ID</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Started</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {jobs.map((job) => (
                <TableRow
                  key={job.jobId}
                  hover
                  sx={{ cursor: 'pointer' }}
                  onClick={() => navigate(`/jobs/${job.jobId}`)}
                >
                  <TableCell sx={{ fontFamily: 'monospace' }}>
                    {job.jobId || '(empty)'}
                  </TableCell>
                  <TableCell>
                    <JobStatusChip state={job.state} />
                  </TableCell>
                  <TableCell>
                    {job.startTime ? new Date(Number(job.startTime)).toLocaleString() : '-'}
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
