import { useState, useEffect } from 'react';
import { useJobSubmit } from '../hooks/useJobSubmit';

interface JobSubmitterProps {
  userId?: string;
  onJobCreated?: (jobId: string, source: string) => void;
}

export function JobSubmitter({ userId = 'local', onJobCreated }: JobSubmitterProps) {
  const [source, setSource] = useState('cat /dev/kafka/local/topics/books | grep Station');
  const mutation = useJobSubmit(userId);

  useEffect(() => {
    if (mutation.isSuccess && onJobCreated) {
      onJobCreated(mutation.data.jobId, source);
    }
  }, [mutation.isSuccess, mutation.data, onJobCreated, source]);

  const handleSubmit = () => {
    mutation.mutate({
      userId,
      source,
    });
  };

  return (
    <div>
      <h2>Submit Text Pipeline</h2>
      <textarea
        value={source}
        onChange={(e) => setSource(e.target.value)}
        rows={4}
        cols={50}
        placeholder='Enter pipeline, e.g. "cat topics | grep Station"'
      />
      <div>
        <button onClick={handleSubmit} disabled={mutation.isPending}>
          {mutation.isPending ? 'Submitting...' : 'Run Pipeline'}
        </button>
      </div>
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
