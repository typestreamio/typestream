import { createTheme } from '@mui/material/styles';

// TypeStream Minimalist Theme
export const theme = createTheme({
  palette: {
    primary: {
      main: '#1976d2', // Blue 600
    },
    secondary: {
      main: '#616161', // Gray 700
    },
    success: {
      main: '#43a047', // Green 600
    },
    error: {
      main: '#e53935', // Red 600
    },
    background: {
      default: '#fafafa', // Gray 50
      paper: '#ffffff',
    },
    text: {
      primary: '#2d3748',
      secondary: '#718096',
    },
  },
  typography: {
    fontFamily: [
      'system-ui',
      '-apple-system',
      '"Segoe UI"',
      'Roboto',
      '"Helvetica Neue"',
      'Arial',
      'sans-serif',
    ].join(','),
    // Use consistent sizing for minimalist aesthetic
    h1: {
      fontSize: '2.5rem',
      fontWeight: 500,
    },
    h2: {
      fontSize: '2rem',
      fontWeight: 500,
    },
    h3: {
      fontSize: '1.75rem',
      fontWeight: 500,
    },
    h4: {
      fontSize: '1.5rem',
      fontWeight: 500,
    },
    h5: {
      fontSize: '1.25rem',
      fontWeight: 500,
    },
    h6: {
      fontSize: '1rem',
      fontWeight: 500,
    },
    body1: {
      fontSize: '1rem',
    },
    body2: {
      fontSize: '0.875rem',
    },
  },
  components: {
    // Card: subtle elevation and border radius
    MuiCard: {
      styleOverrides: {
        root: {
          borderRadius: 8,
          boxShadow: '0 1px 3px rgba(0, 0, 0, 0.12), 0 1px 2px rgba(0, 0, 0, 0.24)',
        },
      },
    },
    // Button: preserve case (no uppercase transformation)
    MuiButton: {
      styleOverrides: {
        root: {
          textTransform: 'none',
        },
      },
      defaultProps: {
        size: 'small',
      },
    },
    // IconButton: consistent sizing
    MuiIconButton: {
      defaultProps: {
        size: 'small',
      },
    },
    // Table: dense spacing for data-heavy views
    MuiTable: {
      defaultProps: {
        size: 'small',
      },
    },
    MuiTableCell: {
      styleOverrides: {
        root: {
          padding: '12px 16px',
        },
        head: {
          fontWeight: 600,
          backgroundColor: '#f5f5f5',
        },
      },
    },
    // Chip: smaller size for status indicators
    MuiChip: {
      defaultProps: {
        size: 'small',
      },
    },
    // TextField: consistent sizing
    MuiTextField: {
      defaultProps: {
        size: 'small',
      },
    },
    // Drawer: clean background
    MuiDrawer: {
      styleOverrides: {
        paper: {
          backgroundColor: '#ffffff',
          borderRight: '1px solid #e2e8f0',
        },
      },
    },
  },
});
