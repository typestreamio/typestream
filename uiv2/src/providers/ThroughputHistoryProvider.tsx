import { createContext, useContext, useRef, useCallback, type ReactNode } from 'react';
import type { JobInfo } from '../generated/job_pb';

const MAX_HISTORY_POINTS = 120; // 2 minutes at 1-second intervals

interface ThroughputHistoryContextValue {
  /** Get the throughput history for a specific job */
  getHistory: (jobId: string) => number[];
  /** Record throughput values from a batch of jobs (called on each poll) */
  recordValues: (jobs: JobInfo[]) => void;
}

const ThroughputHistoryContext = createContext<ThroughputHistoryContextValue | null>(null);

interface ThroughputHistoryProviderProps {
  children: ReactNode;
}

/**
 * Provider that stores historical throughput data for sparkline visualization.
 * Maintains a rolling 2-minute window (120 points) of messagesPerSecond values per job.
 */
export function ThroughputHistoryProvider({ children }: ThroughputHistoryProviderProps) {
  // Use ref to avoid re-renders when updating history
  const historyRef = useRef<Map<string, number[]>>(new Map());

  const getHistory = useCallback((jobId: string): number[] => {
    return historyRef.current.get(jobId) ?? [];
  }, []);

  const recordValues = useCallback((jobs: JobInfo[]) => {
    const currentJobIds = new Set(jobs.map((j) => j.jobId));

    // Record values for each job
    for (const job of jobs) {
      const value = job.throughput?.messagesPerSecond ?? 0;
      const history = historyRef.current.get(job.jobId) ?? [];

      // Add new value
      history.push(value);

      // Keep only the last MAX_HISTORY_POINTS
      if (history.length > MAX_HISTORY_POINTS) {
        history.shift();
      }

      historyRef.current.set(job.jobId, history);
    }

    // Clean up jobs that no longer exist
    for (const jobId of historyRef.current.keys()) {
      if (!currentJobIds.has(jobId)) {
        historyRef.current.delete(jobId);
      }
    }
  }, []);

  return (
    <ThroughputHistoryContext.Provider value={{ getHistory, recordValues }}>
      {children}
    </ThroughputHistoryContext.Provider>
  );
}

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
