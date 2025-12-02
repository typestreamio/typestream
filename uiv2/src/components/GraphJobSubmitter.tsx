import { useEffect } from 'react';
import { useGraphJobSubmit } from '../hooks/useGraphJobSubmit';
import type { CreateJobFromGraphRequest } from '../generated/job_pb';
import { Encoding } from '../generated/job_pb';

interface GraphJobSubmitterProps {
  userId?: string;
  onJobCreated?: (jobId: string, graph: any) => void;
}

export function GraphJobSubmitter({ userId = 'local', onJobCreated }: GraphJobSubmitterProps) {
  const mutation = useGraphJobSubmit(userId);

  const request: CreateJobFromGraphRequest = {
    userId,
    graph: {
      nodes: [
        {
          id: 'source-1',
          nodeType: {
            case: 'streamSource',
            value: {
              stream: { path: '/dev/kafka/local/topics/books' },
              encoding: Encoding.STRING,
            },
          },
        },
        {
          id: 'filter-1',
          nodeType: {
            case: 'filter',
            value: {
              byKey: false,
              predicate: { expr: 'Station' },
            },
          },
        },
      ],
      edges: [
        {
          fromId: 'source-1',
          toId: 'filter-1',
        },
      ],
    },
  };

  useEffect(() => {
    if (mutation.isSuccess && onJobCreated) {
      onJobCreated(mutation.data.jobId, request.graph);
    }
  }, [mutation.isSuccess, mutation.data, onJobCreated]);

  const handleSubmit = () => {
    mutation.mutate(request);
  };

  return (
    <div>
      <h2>Submit Visual Pipeline</h2>
      <p>Example: StreamSource(/dev/kafka/local/topics/books) → Filter("Station")</p>
      <button onClick={handleSubmit} disabled={mutation.isPending}>
        {mutation.isPending ? 'Submitting...' : 'Run Visual Pipeline'}
      </button>
      {mutation.isSuccess && (
        <div style={{ color: 'green' }}>
          Job created successfully! ID: {mutation.data.jobId}
        </div>
      )}
      {mutation.isError && (
        <div style={{ color: 'red' }}>
          Error: {mutation.error.message}
        </div>
      )}
    </div>
  );
}
