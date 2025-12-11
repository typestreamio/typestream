import { Drawer, List, ListItemButton, ListItemIcon, ListItemText, Toolbar } from '@mui/material';
import { useLocation, useNavigate } from 'react-router-dom';
import StreamIcon from '@mui/icons-material/Stream';
import IntegrationInstructionsIcon from '@mui/icons-material/IntegrationInstructions';

const SIDEBAR_WIDTH = 240;

export function Sidebar() {
  const location = useLocation();
  const navigate = useNavigate();

  const menuItems = [
    {
      text: 'Streams',
      icon: <StreamIcon />,
      path: '/streams',
    },
    {
      text: 'Integrations',
      icon: <IntegrationInstructionsIcon />,
      path: '/integrations',
    },
  ];

  return (
    <Drawer
      variant="permanent"
      sx={{
        width: SIDEBAR_WIDTH,
        flexShrink: 0,
        '& .MuiDrawer-paper': {
          width: SIDEBAR_WIDTH,
          boxSizing: 'border-box',
        },
      }}
    >
      <Toolbar />
      <List>
        {menuItems.map((item) => (
          <ListItemButton
            key={item.path}
            selected={location.pathname.startsWith(item.path)}
            onClick={() => navigate(item.path)}
          >
            <ListItemIcon>{item.icon}</ListItemIcon>
            <ListItemText primary={item.text} />
          </ListItemButton>
        ))}
      </List>
    </Drawer>
  );
}
