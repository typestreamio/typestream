import Drawer from '@mui/material/Drawer';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Toolbar from '@mui/material/Toolbar';
import Divider from '@mui/material/Divider';
import Typography from '@mui/material/Typography';
import IconButton from '@mui/material/IconButton';
import Box from '@mui/material/Box';
import Tooltip from '@mui/material/Tooltip';
import WorkIcon from '@mui/icons-material/Work';
import StorageIcon from '@mui/icons-material/Storage';
import CableIcon from '@mui/icons-material/Cable';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import { useLocation, useNavigate } from 'react-router-dom';
import { DRAWER_WIDTH, DRAWER_WIDTH_COLLAPSED } from './constants';

interface SidebarProps {
  open: boolean;
  onToggle: () => void;
}

export function Sidebar({ open, onToggle }: SidebarProps) {
  const location = useLocation();
  const navigate = useNavigate();

  const isActive = (path: string) => location.pathname.startsWith(path);

  const currentWidth = open ? DRAWER_WIDTH : DRAWER_WIDTH_COLLAPSED;

  return (
    <Drawer
      variant="permanent"
      sx={{
        width: currentWidth,
        flexShrink: 0,
        transition: 'width 200ms',
        '& .MuiDrawer-paper': {
          width: currentWidth,
          boxSizing: 'border-box',
          overflowX: 'hidden',
          transition: 'width 200ms',
        },
      }}
    >
      <Toolbar />
      <List>
        <ListItem disablePadding>
          <Tooltip title={open ? '' : 'Jobs'} placement="right">
            <ListItemButton
              selected={isActive('/jobs')}
              onClick={() => navigate('/jobs')}
              sx={{ px: open ? 2 : 2.5, justifyContent: open ? 'initial' : 'center' }}
            >
              <ListItemIcon sx={{ minWidth: open ? 56 : 'auto' }}>
                <WorkIcon />
              </ListItemIcon>
              {open && <ListItemText primary="Jobs" />}
            </ListItemButton>
          </Tooltip>
        </ListItem>
        <ListItem disablePadding>
          <Tooltip title={open ? '' : 'Connections'} placement="right">
            <ListItemButton
              selected={isActive('/connections')}
              onClick={() => navigate('/connections')}
              sx={{ px: open ? 2 : 2.5, justifyContent: open ? 'initial' : 'center' }}
            >
              <ListItemIcon sx={{ minWidth: open ? 56 : 'auto' }}>
                <StorageIcon />
              </ListItemIcon>
              {open && <ListItemText primary="Connections" />}
            </ListItemButton>
          </Tooltip>
        </ListItem>
      </List>
      <Divider sx={{ my: 1 }} />
      {open && (
        <Typography variant="caption" color="text.secondary" sx={{ px: 2, py: 1 }}>
          Advanced
        </Typography>
      )}
      <List dense>
        <ListItem disablePadding>
          <Tooltip title={open ? '' : 'Kafka Connectors'} placement="right">
            <ListItemButton
              selected={isActive('/connectors')}
              onClick={() => navigate('/connectors')}
              sx={{ px: open ? 2 : 2.5, justifyContent: open ? 'initial' : 'center' }}
            >
              <ListItemIcon sx={{ minWidth: open ? 56 : 'auto' }}>
                <CableIcon />
              </ListItemIcon>
              {open && <ListItemText primary="Kafka Connectors" secondary="Debug" />}
            </ListItemButton>
          </Tooltip>
        </ListItem>
      </List>
      <Box sx={{ flexGrow: 1 }} />
      <Divider />
      <Box sx={{ display: 'flex', justifyContent: open ? 'flex-end' : 'center', p: 1 }}>
        <IconButton onClick={onToggle} size="small">
          {open ? <ChevronLeftIcon /> : <ChevronRightIcon />}
        </IconButton>
      </Box>
    </Drawer>
  );
}
