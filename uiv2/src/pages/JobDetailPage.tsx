import Box from '@mui/material/Box';
import Breadcrumbs from '@mui/material/Breadcrumbs';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Divider from '@mui/material/Divider';
import Link from '@mui/material/Link';
import Typography from '@mui/material/Typography';
import CircularProgress from '@mui/material/CircularProgress';
import Alert from '@mui/material/Alert';
import { useParams, useNavigate } from 'react-router-dom';
import { useListJobs } from '../hooks/useListJobs';
import { JobStatusChip } from '../components/JobStatusChip';

export function JobDetailPage() {
  const { jobId } = useParams<{ jobId: string }>();
  const navigate = useNavigate();
  const { data, isLoading, error } = useListJobs('local');

  const job = data?.jobs.find((j) => j.jobId === jobId);

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (error) {
    return (
      <Alert severity="error">
        Error loading job: {error.message}
      </Alert>
    );
  }

  if (!job) {
    return (
      <Alert severity="warning">
        Job not found: {jobId}
      </Alert>
    );
  }

  return (
    <Box>
      <Breadcrumbs sx={{ mb: 3 }}>
        <Link
          component="button"
          underline="hover"
          color="inherit"
          onClick={() => navigate('/jobs')}
        >
          Jobs
        </Link>
        <Typography color="text.primary">{jobId}</Typography>
      </Breadcrumbs>

      <Card>
        <CardContent>
          <Typography variant="h5" gutterBottom>
            Job Details
          </Typography>
          <Divider sx={{ my: 2 }} />

          <Box sx={{ display: 'grid', gap: 2 }}>
            <Box>
              <Typography variant="subtitle2" color="text.secondary">
                Job ID
              </Typography>
              <Typography variant="body1" sx={{ fontFamily: 'monospace' }}>
                {job.jobId}
              </Typography>
            </Box>

            <Box>
              <Typography variant="subtitle2" color="text.secondary">
                Status
              </Typography>
              <JobStatusChip state={job.state} />
            </Box>

            <Box>
              <Typography variant="subtitle2" color="text.secondary">
                Start Time
              </Typography>
              <Typography variant="body1">
                {job.startTime ? new Date(Number(job.startTime)).toLocaleString() : '-'}
              </Typography>
            </Box>

            {job.graph && (
              <>
                <Divider sx={{ my: 1 }} />
                <Typography variant="h6" gutterBottom>
                  Pipeline Graph
                </Typography>

                <Box>
                  <Typography variant="subtitle2" color="text.secondary">
                    Nodes ({job.graph.nodes.length})
                  </Typography>
                  {job.graph.nodes.map((node, idx) => (
                    <Typography key={idx} variant="body2" sx={{ fontFamily: 'monospace', ml: 2 }}>
                      {node.id}: {node.nodeType.case ?? 'unknown'}
                    </Typography>
                  ))}
                </Box>

                <Box>
                  <Typography variant="subtitle2" color="text.secondary">
                    Edges ({job.graph.edges.length})
                  </Typography>
                  {job.graph.edges.map((edge, idx) => (
                    <Typography key={idx} variant="body2" sx={{ fontFamily: 'monospace', ml: 2 }}>
                      {edge.fromId} → {edge.toId}
                    </Typography>
                  ))}
                </Box>
              </>
            )}
          </Box>
        </CardContent>
      </Card>
    </Box>
  );
}
