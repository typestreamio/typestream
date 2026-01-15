import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';
import Tooltip from '@mui/material/Tooltip';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import type { ReactNode } from 'react';

interface BaseNodeProps {
  title: string;
  icon?: ReactNode;
  error?: string;
  isInferring?: boolean;
  children: ReactNode;
}

export function BaseNode({ title, icon, error, isInferring, children }: BaseNodeProps) {
  return (
    <Paper
      elevation={3}
      sx={{
        minWidth: 220,
        bgcolor: 'background.paper',
        border: error ? '2px solid' : '1px solid',
        borderColor: error ? 'error.main' : 'divider',
        opacity: isInferring ? 0.7 : 1,
        transition: 'border-color 0.2s, opacity 0.2s',
      }}
    >
      <Box
        sx={{
          px: 1.5,
          py: 1,
          borderBottom: '1px solid',
          borderColor: 'divider',
          bgcolor: 'action.hover',
          display: 'flex',
          alignItems: 'center',
          gap: 1,
        }}
      >
        {icon}
        <Typography variant="subtitle2" fontWeight="bold" sx={{ flex: 1 }}>
          {title}
        </Typography>
        {isInferring && <CircularProgress size={14} />}
        {error && (
          <Tooltip title={error} arrow>
            <ErrorOutlineIcon color="error" fontSize="small" />
          </Tooltip>
        )}
      </Box>
      <Box sx={{ p: 1.5 }}>{children}</Box>
    </Paper>
  );
}
