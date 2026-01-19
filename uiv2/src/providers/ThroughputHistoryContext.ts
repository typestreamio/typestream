import { createContext, useContext } from 'react';
import type { JobInfo } from '../generated/job_pb';

export const MAX_HISTORY_POINTS = 120; // 2 minutes at 1-second intervals

export interface ThroughputHistoryContextValue {
  /** Get the throughput history for a specific job */
  getHistory: (jobId: string) => number[];
  /** Record throughput values from a batch of jobs (called on each poll) */
  recordValues: (jobs: JobInfo[]) => void;
  /** Version counter that increments on each update (for reactivity) */
  version: number;
}

export const ThroughputHistoryContext = createContext<ThroughputHistoryContextValue | null>(null);

/**
 * Hook to access throughput history context.
 * Must be used within a ThroughputHistoryProvider.
 */
export function useThroughputHistoryContext() {
  const context = useContext(ThroughputHistoryContext);
  if (!context) {
    throw new Error('useThroughputHistoryContext must be used within a ThroughputHistoryProvider');
  }
  return context;
}
