import Chip from '@mui/material/Chip';
import { JobState } from '../generated/job_pb';

const stateConfig: Record<JobState, { label: string; color: 'success' | 'error' | 'warning' | 'default' }> = {
  [JobState.RUNNING]: { label: 'Running', color: 'success' },
  [JobState.STARTING]: { label: 'Starting', color: 'warning' },
  [JobState.STOPPING]: { label: 'Stopping', color: 'warning' },
  [JobState.STOPPED]: { label: 'Stopped', color: 'default' },
  [JobState.FAILED]: { label: 'Failed', color: 'error' },
  [JobState.UNKNOWN]: { label: 'Unknown', color: 'default' },
  [JobState.JOB_STATE_UNSPECIFIED]: { label: 'Unspecified', color: 'default' },
};

interface JobStatusChipProps {
  state: JobState;
}

export function JobStatusChip({ state }: JobStatusChipProps) {
  const config = stateConfig[state] ?? stateConfig[JobState.UNKNOWN];
  return <Chip label={config.label} color={config.color} size="small" />;
}