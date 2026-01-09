import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';
import type { ReactNode } from 'react';

interface BaseNodeProps {
  title: string;
  icon?: ReactNode;
  children: ReactNode;
}

export function BaseNode({ title, icon, children }: BaseNodeProps) {
  return (
    <Paper
      elevation={3}
      sx={{
        minWidth: 220,
        bgcolor: 'background.paper',
        border: '1px solid',
        borderColor: 'divider',
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
        <Typography variant="subtitle2" fontWeight="bold">
          {title}
        </Typography>
      </Box>
      <Box sx={{ p: 1.5 }}>{children}</Box>
    </Paper>
  );
}
