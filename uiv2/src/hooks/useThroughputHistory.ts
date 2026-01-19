import { useState, useEffect } from 'react';
import { useThroughputHistoryContext } from '../providers/ThroughputHistoryProvider';

/**
 * Hook to get throughput history for a specific job.
 * Returns an array of messagesPerSecond values over time.
 *
 * @param jobId - The job ID to get history for
 * @returns Array of throughput values (max 120 points = 2 minutes)
 */
export function useThroughputHistory(jobId: string): number[] {
  const { getHistory } = useThroughputHistoryContext();
  const [history, setHistory] = useState<number[]>(() => getHistory(jobId));

  // Poll for updates since the context uses refs (no re-renders on update)
  useEffect(() => {
    const interval = setInterval(() => {
      setHistory(getHistory(jobId));
    }, 1000);

    return () => clearInterval(interval);
  }, [jobId, getHistory]);

  return history;
}
