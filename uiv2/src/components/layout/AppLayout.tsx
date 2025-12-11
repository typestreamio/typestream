import { Box, AppBar, Toolbar, Typography } from '@mui/material';
import { Outlet } from 'react-router-dom';
import { Sidebar } from './Sidebar';

const SIDEBAR_WIDTH = 240;

export function AppLayout() {
  return (
    <Box sx={{ display: 'flex' }}>
      {/* App Bar */}
      <AppBar
        position="fixed"
        sx={{
          zIndex: (theme) => theme.zIndex.drawer + 1,
        }}
      >
        <Toolbar>
          <Typography variant="h6" noWrap component="div">
            TypeStream
          </Typography>
        </Toolbar>
      </AppBar>

      {/* Sidebar */}
      <Sidebar />

      {/* Main Content */}
      <Box
        component="main"
        sx={{
          flexGrow: 1,
          p: 3,
          width: { sm: `calc(100% - ${SIDEBAR_WIDTH}px)` },
        }}
      >
        <Toolbar /> {/* Spacer for fixed AppBar */}
        <Outlet />
      </Box>
    </Box>
  );
}
