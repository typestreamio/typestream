import { useEffect, useMemo } from 'react';
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
import { useNavigate } from 'react-router-dom';
import { useListJobs } from '../hooks/useListJobs';
import { JobStatusChip } from '../components/JobStatusChip';
import { formatThroughput, formatBytes } from '../utils/formatters';
import { Sparkline } from '../components/Sparkline';
import { useThroughputHistoryContext } from '../providers/ThroughputHistoryContext';
import { useThroughputHistory } from '../hooks/useThroughputHistory';
import { useServerConnection } from '../providers/ServerConnectionContext';
import type { JobInfo } from '../generated/job_pb';

interface JobRowProps {
  job: JobInfo;
  onClick: () => void;
}

function JobRow({ job, onClick }: JobRowProps) {
  const history = useThroughputHistory(job.jobId);

  return (
    <TableRow
      hover
      sx={{ cursor: 'pointer' }}
      onClick={onClick}
    >
      <TableCell sx={{ fontFamily: 'monospace' }}>
        {job.jobId || '(empty)'}
      </TableCell>
      <TableCell>
        <JobStatusChip state={job.state} />
      </TableCell>
      <TableCell sx={{ fontFamily: 'monospace' }}>
        {formatThroughput(job.throughput?.messagesPerSecond ?? 0)}
      </TableCell>
      <TableCell>
        <Sparkline data={history} width={80} height={24} />
      </TableCell>
      <TableCell sx={{ fontFamily: 'monospace' }}>
        {formatBytes(job.throughput?.bytesPerSecond ?? 0)}/s
      </TableCell>
      <TableCell>
        {job.startTime ? new Date(Number(job.startTime)).toLocaleString() : '-'}
      </TableCell>
    </TableRow>
  );
}

export function JobsPage() {
  const navigate = useNavigate();
  const { data, isLoading, error, refetch } = useListJobs('local');
  const { recordValues } = useThroughputHistoryContext();
  const { isConnected } = useServerConnection();

  const jobs = useMemo(() => data?.jobs ?? [], [data?.jobs]);

  // Don't show errors when server is disconnected (banner handles it)
  const showError = error && isConnected;

  // Record throughput values on each poll for sparkline history
  useEffect(() => {
    if (jobs.length > 0) {
      recordValues(jobs);
    }
  }, [jobs, recordValues]);

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="h4">Jobs</Typography>
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
          Error loading jobs. Please try again.
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
                <TableCell>Throughput</TableCell>
                <TableCell>Activity</TableCell>
                <TableCell>Bandwidth</TableCell>
                <TableCell>Started</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {jobs.map((job) => (
                <JobRow
                  key={job.jobId}
                  job={job}
                  onClick={() => navigate(`/jobs/${job.jobId}`)}
                />
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </Box>
  );
}
