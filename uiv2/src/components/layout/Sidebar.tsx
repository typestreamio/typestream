import Drawer from '@mui/material/Drawer';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Toolbar from '@mui/material/Toolbar';
import Divider from '@mui/material/Divider';
import Typography from '@mui/material/Typography';
import WorkIcon from '@mui/icons-material/Work';
import StorageIcon from '@mui/icons-material/Storage';
import CableIcon from '@mui/icons-material/Cable';
import { useLocation, useNavigate } from 'react-router-dom';
import { DRAWER_WIDTH } from './constants';

export function Sidebar() {
  const location = useLocation();
  const navigate = useNavigate();

  const isActive = (path: string) => location.pathname.startsWith(path);

  return (
    <Drawer
      variant="permanent"
      sx={{
        width: DRAWER_WIDTH,
        flexShrink: 0,
        '& .MuiDrawer-paper': {
          width: DRAWER_WIDTH,
          boxSizing: 'border-box',
        },
      }}
    >
      <Toolbar />
      <List>
        <ListItem disablePadding>
          <ListItemButton
            selected={isActive('/jobs')}
            onClick={() => navigate('/jobs')}
          >
            <ListItemIcon>
              <WorkIcon />
            </ListItemIcon>
            <ListItemText primary="Jobs" />
          </ListItemButton>
        </ListItem>
        <ListItem disablePadding>
          <ListItemButton
            selected={isActive('/connections')}
            onClick={() => navigate('/connections')}
          >
            <ListItemIcon>
              <StorageIcon />
            </ListItemIcon>
            <ListItemText primary="Connections" />
          </ListItemButton>
        </ListItem>
      </List>
      <Divider sx={{ my: 1 }} />
      <Typography variant="caption" color="text.secondary" sx={{ px: 2, py: 1 }}>
        Advanced
      </Typography>
      <List dense>
        <ListItem disablePadding>
          <ListItemButton
            selected={isActive('/connectors')}
            onClick={() => navigate('/connectors')}
          >
            <ListItemIcon>
              <CableIcon />
            </ListItemIcon>
            <ListItemText primary="Kafka Connectors" secondary="Debug" />
          </ListItemButton>
        </ListItem>
      </List>
    </Drawer>
  );
}
