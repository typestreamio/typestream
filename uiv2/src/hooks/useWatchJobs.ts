import { useEffect, useState, useRef, useCallback } from 'react';
import { createClient } from '@connectrpc/connect';
import { useQueryClient } from '@tanstack/react-query';
import { transport } from '../services/transport';
import { JobService } from '../generated/job_connect';
import { JobInfo, ListJobsResponse } from '../generated/job_pb';

/**
 * Hook that watches for job updates via server-streaming RPC.
 * Jobs are updated in near-realtime as the stream receives updates.
 */
export function useWatchJobs(userId: string = 'local') {
  const [jobs, setJobs] = useState<JobInfo[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [isConnected, setIsConnected] = useState(false);
  const abortControllerRef = useRef<AbortController | null>(null);
  const queryClient = useQueryClient();

  const connect = useCallback(async () => {
    // Abort any existing connection
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }

    const abortController = new AbortController();
    abortControllerRef.current = abortController;

    setIsLoading(true);
    setError(null);
    setIsConnected(false);

    try {
      const client = createClient(JobService, transport);
      const stream = client.watchJobs(
        { userId },
        { signal: abortController.signal }
      );

      // Stream is open - mark as connected
      // Server will emit updates when job state changes
      setIsLoading(false);
      setIsConnected(true);

      // Track jobs by ID for updates
      const jobMap = new Map<string, JobInfo>();

      for await (const jobInfo of stream) {
        if (abortController.signal.aborted) break;

        // Update job in map
        jobMap.set(jobInfo.jobId, jobInfo);

        // Update state with all jobs
        const allJobs = Array.from(jobMap.values());
        setJobs(allJobs);

        // Sync to React Query cache so useListJobs stays current
        queryClient.setQueryData(
          ['io.typestream.grpc.JobService', 'ListJobs', { userId }],
          new ListJobsResponse({ jobs: allJobs })
        );
      }
    } catch (err) {
      if (abortController.signal.aborted) {
        // Expected when component unmounts or reconnects
        return;
      }
      console.error('WatchJobs stream error:', err);
      setError(err instanceof Error ? err : new Error('Stream error'));
      setIsLoading(false);
      setIsConnected(false);
    }
  }, [userId, queryClient]);

  // Start streaming on mount
  useEffect(() => {
    connect();

    return () => {
      // Cleanup: abort stream on unmount
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }
    };
  }, [connect]);

  // Reconnect function for manual retry
  const reconnect = useCallback(() => {
    setJobs([]);
    connect();
  }, [connect]);

  return {
    jobs,
    isLoading,
    error,
    isConnected,
    reconnect,
  };
}
