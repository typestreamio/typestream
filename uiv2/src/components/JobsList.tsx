import { useListJobs } from '../hooks/useListJobs';
import { JobState } from '../generated/job_pb';

function getStateLabel(state: JobState): string {
  switch (state) {
    case JobState.STARTING:
      return 'Starting';
    case JobState.RUNNING:
      return 'Running';
    case JobState.STOPPING:
      return 'Stopping';
    case JobState.STOPPED:
      return 'Stopped';
    case JobState.FAILED:
      return 'Failed';
    case JobState.UNKNOWN:
      return 'Unknown';
    default:
      return 'Unspecified';
  }
}

function getStateColor(state: JobState): string {
  switch (state) {
    case JobState.RUNNING:
      return '#22c55e';
    case JobState.STARTING:
      return '#eab308';
    case JobState.STOPPING:
      return '#f97316';
    case JobState.STOPPED:
      return '#6b7280';
    case JobState.FAILED:
      return '#ef4444';
    default:
      return '#9ca3af';
  }
}

interface JobsListProps {
  userId?: string;
}

export function JobsList({ userId = 'local' }: JobsListProps) {
  const { data, isLoading, error, refetch } = useListJobs(userId);

  if (isLoading) return <div>Loading jobs...</div>;
  if (error) return <div style={{ color: 'red' }}>Error: {error.message}</div>;

  const jobs = data?.jobs ?? [];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
        <h2>Jobs</h2>
        <button onClick={() => refetch()}>Refresh</button>
      </div>

      {jobs.length === 0 ? (
        <p>No jobs found. Submit a pipeline to create a job.</p>
      ) : (
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead>
            <tr style={{ borderBottom: '2px solid #e5e7eb' }}>
              <th style={{ textAlign: 'left', padding: '0.5rem' }}>Job ID</th>
              <th style={{ textAlign: 'left', padding: '0.5rem' }}>State</th>
              <th style={{ textAlign: 'left', padding: '0.5rem' }}>Started</th>
            </tr>
          </thead>
          <tbody>
            {jobs.map((job) => (
              <tr key={job.jobId} style={{ borderBottom: '1px solid #e5e7eb' }}>
                <td style={{ padding: '0.5rem', fontFamily: 'monospace' }}>
                  {job.jobId || '(empty)'}
                </td>
                <td style={{ padding: '0.5rem' }}>
                  <span style={{
                    backgroundColor: getStateColor(job.state),
                    color: 'white',
                    padding: '0.25rem 0.5rem',
                    borderRadius: '4px',
                    fontSize: '0.875rem',
                  }}>
                    {getStateLabel(job.state)}
                  </span>
                </td>
                <td style={{ padding: '0.5rem' }}>
                  {job.startTime ? new Date(Number(job.startTime)).toLocaleString() : '-'}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
