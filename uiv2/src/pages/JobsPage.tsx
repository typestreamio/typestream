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
import { useCreateJob } from '../hooks/useCreateJob';
import { JobStatusChip } from '../components/JobStatusChip';
import {
  CreateJobFromGraphRequest,
  PipelineGraph,
  PipelineNode,
  PipelineEdge,
  StreamSourceNode,
  FilterNode,
  DataStreamProto,
  PredicateProto,
  Encoding,
} from '../generated/job_pb';

export function JobsPage() {
  const navigate = useNavigate();
  const { data, isLoading, error, refetch } = useListJobs('local');
  const createJob = useCreateJob();

  const jobs = data?.jobs ?? [];

  const handleCreateTestJob = () => {
    const sourceNode = new PipelineNode({
      id: 'source-1',
      nodeType: {
        case: 'streamSource',
        value: new StreamSourceNode({
          dataStream: new DataStreamProto({ path: '/dev/kafka/local/topics/books' }),
          encoding: Encoding.AVRO,
        }),
      },
    });

    const filterNode = new PipelineNode({
      id: 'filter-1',
      nodeType: {
        case: 'filter',
        value: new FilterNode({
          byKey: false,
          predicate: new PredicateProto({ expr: 'Station' }),
        }),
      },
    });

    const graph = new PipelineGraph({
      nodes: [sourceNode, filterNode],
      edges: [new PipelineEdge({ fromId: 'source-1', toId: 'filter-1' })],
    });

    const request = new CreateJobFromGraphRequest({ userId: 'local', graph });
    createJob.mutate(request, {
      onSuccess: () => refetch(),
    });
  };

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="h4">Jobs</Typography>
        <Box sx={{ display: 'flex', gap: 1 }}>
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={handleCreateTestJob}
            disabled={createJob.isPending}
          >
            {createJob.isPending ? 'Creating...' : 'Create Test Job'}
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

      {createJob.isError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          Error creating job: {createJob.error.message}
        </Alert>
      )}

      {createJob.isSuccess && createJob.data?.success && (
        <Alert severity="success" sx={{ mb: 2 }}>
          Job created: {createJob.data.jobId}
        </Alert>
      )}

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
