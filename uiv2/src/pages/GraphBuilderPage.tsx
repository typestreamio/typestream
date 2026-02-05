import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import { useNavigate } from 'react-router-dom';
import { GraphBuilder } from '../components/graph-builder/GraphBuilder';

export function GraphBuilderPage() {
  const navigate = useNavigate();

  return (
    <Box sx={{ height: 'calc(100vh - 140px)', display: 'flex', flexDirection: 'column' }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2 }}>
        <Button
          startIcon={<ArrowBackIcon />}
          onClick={() => navigate('/jobs')}
        >
          Back to Jobs
        </Button>
        <Typography variant="h5">Create New Job</Typography>
      </Box>
      <Box sx={{ flex: 1, minHeight: 0 }}>
        <GraphBuilder />
      </Box>
    </Box>
  );
}
