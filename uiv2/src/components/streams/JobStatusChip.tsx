import { Chip } from '@mui/material';

interface JobStatusChipProps {
  state: string;
}

export function JobStatusChip({ state }: JobStatusChipProps) {
  // Determine color based on state
  const getColor = () => {
    const upperState = state.toUpperCase();
    if (upperState.includes('RUNNING') || upperState.includes('ACTIVE')) {
      return 'success';
    } else if (upperState.includes('STOPPED') || upperState.includes('PAUSED')) {
      return 'default';
    } else if (upperState.includes('FAILED') || upperState.includes('ERROR')) {
      return 'error';
    } else {
      return 'default';
    }
  };

  return (
    <Chip
      label={state}
      color={getColor()}
      size="small"
    />
  );
}
