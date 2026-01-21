import AppBar from '@mui/material/AppBar';
import Box from '@mui/material/Box';
import Toolbar from '@mui/material/Toolbar';
import Typography from '@mui/material/Typography';
import { Outlet } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { ServerStatusBanner } from './ServerStatusBanner';
import { DRAWER_WIDTH } from './constants';

export function AppLayout() {
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
      <Sidebar />
      <Box
        component="main"
        sx={{
          flexGrow: 1,
          width: `calc(100% - ${DRAWER_WIDTH}px)`,
          display: 'flex',
          flexDirection: 'column',
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
