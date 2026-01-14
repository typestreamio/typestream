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
import { useWatchJobs } from '../hooks/useWatchJobs';
import { useListStores } from '../hooks/useListStores';
import { JobStatusChip } from '../components/JobStatusChip';
import { JobState } from '../generated/job_pb';

export function JobDetailPage() {
  const { jobId } = useParams<{ jobId: string }>();
  const navigate = useNavigate();
  const { jobs, isLoading, error } = useWatchJobs('local');
  const { data: storesData } = useListStores();

  const job = jobs.find((j) => j.jobId === jobId);
  const jobStores = storesData?.stores.filter((s) => s.jobId === jobId) ?? [];

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

            {job.state === JobState.RUNNING && jobStores.length > 0 && (
              <>
                <Divider sx={{ my: 1 }} />
                <Typography variant="h6" gutterBottom>
                  State Stores
                </Typography>
                {jobStores.map((store) => (
                  <Box key={store.name} sx={{ mb: 2 }}>
                    <Typography variant="subtitle2">{store.name}</Typography>
                    <Typography variant="body2" color="text.secondary">
                      ~{store.approximateCount.toString()} keys
                    </Typography>
                    <Typography
                      variant="caption"
                      component="pre"
                      sx={{
                        mt: 1,
                        p: 1,
                        bgcolor: 'grey.900',
                        borderRadius: 1,
                        overflow: 'auto',
                        fontFamily: 'monospace',
                      }}
                    >
{`grpcurl -plaintext -d '{"store_name":"${store.name}","limit":10}' \\
  localhost:8080 io.typestream.grpc.StateQueryService/GetAllValues \\
  | jq '{key: .key | fromjson, value: .value | fromjson}'`}
                    </Typography>
                  </Box>
                ))}
              </>
            )}
          </Box>
        </CardContent>
      </Card>
    </Box>
  );
}
