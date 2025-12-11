import { Box, Typography, Breadcrumbs, Link, Card, CardContent, CircularProgress } from '@mui/material';
import { useParams, useNavigate } from 'react-router-dom';
import { useJobsList } from '../hooks/useJobsList';
import { JobStatusChip } from '../components/streams/JobStatusChip';

export function StreamDetailPage() {
  const { jobId } = useParams<{ jobId: string }>();
  const navigate = useNavigate();
  const { data, isLoading } = useJobsList();

  // Find the job from the list
  const job = data?.jobs.find((j: any) => j.jobId === jobId);

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (!job) {
    return (
      <Box>
        <Typography variant="h5" color="error" gutterBottom>
          Job not found
        </Typography>
        <Typography color="text.secondary">
          Job ID: {jobId}
        </Typography>
      </Box>
    );
  }

  // Format full timestamp
  const formatFullTime = (timestamp: bigint) => {
    const date = new Date(Number(timestamp));
    return date.toLocaleString();
  };

  return (
    <Box>
      {/* Breadcrumbs */}
      <Breadcrumbs sx={{ mb: 2 }}>
        <Link
          component="button"
          variant="body1"
          onClick={() => navigate('/streams')}
          sx={{ cursor: 'pointer', textDecoration: 'none', color: 'primary.main' }}
        >
          Streams
        </Link>
        <Typography color="text.primary">{jobId}</Typography>
      </Breadcrumbs>

      {/* Job Details Card */}
      <Card>
        <CardContent>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2 }}>
            <Typography variant="h5">Job Details</Typography>
            <JobStatusChip state={job.state} />
          </Box>

          <Box sx={{ display: 'grid', gap: 2 }}>
            <Box>
              <Typography variant="subtitle2" color="text.secondary">
                Job ID
              </Typography>
              <Typography variant="body1">{job.jobId}</Typography>
            </Box>

            <Box>
              <Typography variant="subtitle2" color="text.secondary">
                Status
              </Typography>
              <Typography variant="body1">{job.state}</Typography>
            </Box>

            <Box>
              <Typography variant="subtitle2" color="text.secondary">
                Started
              </Typography>
              <Typography variant="body1">{formatFullTime(job.startTime)}</Typography>
            </Box>

            {job.graph && (
              <Box>
                <Typography variant="subtitle2" color="text.secondary">
                  Pipeline
                </Typography>
                <Typography variant="body1">
                  {job.graph.nodes.length} nodes, {job.graph.edges.length} edges
                </Typography>
              </Box>
            )}
          </Box>
        </CardContent>
      </Card>

      {/* Future: Show Rete editor or graph visualization here */}
      <Box sx={{ mt: 3 }}>
        <Typography variant="body2" color="text.secondary">
          Graph visualization will appear here in future phases
        </Typography>
      </Box>
    </Box>
  );
}
