import {
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  CircularProgress,
  Typography,
  Box,
} from '@mui/material';
import { useNavigate } from 'react-router-dom';
import { useJobsList } from '../../hooks/useJobsList';
import { JobStatusChip } from './JobStatusChip';

export function StreamsList() {
  const navigate = useNavigate();
  const { data, isLoading, error } = useJobsList();

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (error) {
    return (
      <Typography color="error" sx={{ p: 2 }}>
        Error loading jobs: {error.message}
      </Typography>
    );
  }

  if (!data?.jobs || data.jobs.length === 0) {
    return (
      <Typography color="text.secondary" sx={{ p: 2 }}>
        No running jobs. Click "Create New Job" to get started.
      </Typography>
    );
  }

  // Format timestamp to relative time
  const formatTime = (timestamp: bigint) => {
    const date = new Date(Number(timestamp));
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMins / 60);
    const diffDays = Math.floor(diffHours / 24);

    if (diffDays > 0) {
      return `${diffDays}d ago`;
    } else if (diffHours > 0) {
      return `${diffHours}h ago`;
    } else if (diffMins > 0) {
      return `${diffMins}m ago`;
    } else {
      return 'Just now';
    }
  };

  return (
    <TableContainer component={Paper}>
      <Table>
        <TableHead>
          <TableRow>
            <TableCell>Job ID</TableCell>
            <TableCell>Status</TableCell>
            <TableCell>Start Time</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {data.jobs.map((job: any) => (
            <TableRow
              key={job.jobId}
              hover
              onClick={() => navigate(`/streams/${job.jobId}`)}
              sx={{ cursor: 'pointer' }}
            >
              <TableCell>{job.jobId}</TableCell>
              <TableCell>
                <JobStatusChip state={job.state} />
              </TableCell>
              <TableCell>{formatTime(job.startTime)}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}
