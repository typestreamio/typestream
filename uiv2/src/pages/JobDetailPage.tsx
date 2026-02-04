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
import { useListStores } from '../hooks/useListStores';
import { JobStatusChip } from '../components/JobStatusChip';
import { JobState } from '../generated/job_pb';
import { formatThroughput, formatBytes, formatNumber } from '../utils/formatters';
import { PipelineGraphViewer } from '../components/graph-builder/PipelineGraphViewer';
import { Sparkline } from '../components/Sparkline';
import { useThroughputHistory } from '../hooks/useThroughputHistory';
import { useServerConnection } from '../providers/ServerConnectionContext';
import { getGrpcHost, getWeaviateBaseUrl } from '../utils/endpoints';

export function JobDetailPage() {
  const { jobId } = useParams<{ jobId: string }>();
  const navigate = useNavigate();
  const { data, isLoading, error } = useListJobs('local');
  const { data: storesData } = useListStores();
  const throughputHistory = useThroughputHistory(jobId ?? '');
  const { isConnected } = useServerConnection();

  const job = data?.jobs.find((j) => j.jobId === jobId);
  const jobStores = storesData?.stores.filter((s) => s.jobId === jobId) ?? [];

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
        <CircularProgress />
      </Box>
    );
  }

  // Don't show errors when server is disconnected (banner handles it)
  if (error && isConnected) {
    return (
      <Alert severity="error">
        Error loading job. Please try again.
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

            {job.throughput && (
              <>
                <Divider sx={{ my: 1 }} />
                <Typography variant="h6" gutterBottom>
                  Throughput
                </Typography>
                <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 2 }}>
                  <Box>
                    <Typography variant="subtitle2" color="text.secondary">
                      Messages/sec
                    </Typography>
                    <Typography variant="body1" sx={{ fontFamily: 'monospace' }}>
                      {formatThroughput(job.throughput.messagesPerSecond)}
                    </Typography>
                  </Box>
                  <Box>
                    <Typography variant="subtitle2" color="text.secondary">
                      Bandwidth
                    </Typography>
                    <Typography variant="body1" sx={{ fontFamily: 'monospace' }}>
                      {formatBytes(job.throughput.bytesPerSecond)}/s
                    </Typography>
                  </Box>
                  <Box>
                    <Typography variant="subtitle2" color="text.secondary">
                      Total Messages
                    </Typography>
                    <Typography variant="body1" sx={{ fontFamily: 'monospace' }}>
                      {formatNumber(job.throughput.totalMessages)}
                    </Typography>
                  </Box>
                  <Box>
                    <Typography variant="subtitle2" color="text.secondary">
                      Total Bytes
                    </Typography>
                    <Typography variant="body1" sx={{ fontFamily: 'monospace' }}>
                      {formatBytes(Number(job.throughput.totalBytes))}
                    </Typography>
                  </Box>
                </Box>
                <Box sx={{ mt: 2 }}>
                  <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                    Activity (last 2 minutes)
                  </Typography>
                  <Box className="dark-panel">
                    <Sparkline
                      data={throughputHistory}
                      width={300}
                      height={60}
                    />
                  </Box>
                </Box>
              </>
            )}

            {job.graph && (
              <>
                <Divider sx={{ my: 1 }} />
                <Typography variant="h6" gutterBottom>
                  Pipeline Graph
                </Typography>
                <PipelineGraphViewer
                  graph={job.graph}
                  isRunning={job.state === JobState.RUNNING}
                  height={350}
                />
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
                      className="dark-panel"
                      sx={{
                        mt: 1,
                        overflow: 'auto',
                        fontFamily: 'monospace',
                      }}
                    >
{`grpcurl -plaintext -d '{"store_name":"${store.name}","limit":10}' \\
  ${getGrpcHost()} io.typestream.grpc.StateQueryService/GetAllValues \\
  | jq '{key: .key | fromjson, value: .value | fromjson}'`}
                    </Typography>
                  </Box>
                ))}
              </>
            )}

            {job.state === JobState.RUNNING && job.weaviateSinks.length > 0 && (
              <>
                <Divider sx={{ my: 1 }} />
                <Typography variant="h6" gutterBottom>
                  Weaviate Vector Sinks
                </Typography>
                {job.weaviateSinks.map((sink) => (
                  <Box key={sink.nodeId} sx={{ mb: 2 }}>
                    <Typography variant="subtitle2">
                      Collection: {sink.collectionName}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      ID Strategy: {sink.documentIdStrategy || 'NoIdStrategy'}
                      {sink.documentIdField && ` (field: ${sink.documentIdField})`}
                    </Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                      Vector: {sink.vectorStrategy || 'NoVectorStrategy'}
                      {sink.vectorField && ` (field: ${sink.vectorField})`}
                    </Typography>
                    <Typography
                      variant="caption"
                      component="pre"
                      className="dark-panel"
                      sx={{
                        mt: 1,
                        overflow: 'auto',
                        fontFamily: 'monospace',
                      }}
                    >
{`# Count objects in collection
curl -s '${getWeaviateBaseUrl()}/v1/objects?class=${sink.collectionName}&limit=1' | jq '.totalResults'

# List objects (first 5)
curl -s '${getWeaviateBaseUrl()}/v1/objects?class=${sink.collectionName}&limit=5' | jq '.objects[] | {id, properties}'

# Semantic search
curl -s '${getWeaviateBaseUrl()}/v1/graphql' -X POST \\
  -H 'Content-Type: application/json' \\
  -d '{"query": "{ Get { ${sink.collectionName}(limit: 5, nearText: {concepts: [\\"your search query\\"]}) { _additional { id distance } } } }"}' \\
  | jq '.data.Get.${sink.collectionName}'`}
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
