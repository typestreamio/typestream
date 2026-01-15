import Drawer from '@mui/material/Drawer';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Toolbar from '@mui/material/Toolbar';
import WorkIcon from '@mui/icons-material/Work';
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
            selected={isActive('/connectors')}
            onClick={() => navigate('/connectors')}
          >
            <ListItemIcon>
              <CableIcon />
            </ListItemIcon>
            <ListItemText primary="Connectors" />
          </ListItemButton>
        </ListItem>
      </List>
    </Drawer>
  );
}
