import { useState } from 'react';
import AppBar from '@mui/material/AppBar';
import Box from '@mui/material/Box';
import Toolbar from '@mui/material/Toolbar';
import Typography from '@mui/material/Typography';
import { Outlet } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { ServerStatusBanner } from './ServerStatusBanner';
import { DRAWER_WIDTH, DRAWER_WIDTH_COLLAPSED } from './constants';

export function AppLayout() {
  const [sidebarOpen, setSidebarOpen] = useState(true);

  const currentWidth = sidebarOpen ? DRAWER_WIDTH : DRAWER_WIDTH_COLLAPSED;

  return (
    <Box sx={{ display: 'flex' }}>
      <AppBar
        position="fixed"
        sx={{ zIndex: (theme) => theme.zIndex.drawer + 1 }}
      >
        <Toolbar>
          <Box
            component="img"
            src="/logo.svg"
            alt="TypeStream"
            sx={{ height: 32, mr: 1.5 }}
          />
          <Typography variant="h6" noWrap component="div">
            TypeStream
          </Typography>
        </Toolbar>
      </AppBar>
      <Sidebar open={sidebarOpen} onToggle={() => setSidebarOpen((o) => !o)} />
      <Box
        component="main"
        sx={{
          flexGrow: 1,
          width: `calc(100% - ${currentWidth}px)`,
          display: 'flex',
          flexDirection: 'column',
          transition: 'width 200ms',
        }}
      >
        <Toolbar />
        <ServerStatusBanner />
        <Box sx={{ p: 3, flexGrow: 1 }}>
          <Outlet />
        </Box>
      </Box>
    </Box>
  );
}
