import { useMemo } from 'react';
import { useThroughputHistoryContext } from '../providers/ThroughputHistoryContext';

/**
 * Hook to get throughput history for a specific job.
 * Returns an array of messagesPerSecond values over time.
 *
 * @param jobId - The job ID to get history for
 * @returns Array of throughput values (max 120 points = 2 minutes)
 */
export function useThroughputHistory(jobId: string): number[] {
  const { getHistory, version } = useThroughputHistoryContext();

  // Re-compute when version changes (data was recorded)
  // Note: version is needed to trigger recomputation when new data is recorded
  return useMemo(
    () => getHistory(jobId),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [jobId, getHistory, version]
  );
}
