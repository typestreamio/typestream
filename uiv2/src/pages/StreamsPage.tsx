import { Box, Typography, Button } from '@mui/material';
import { useNavigate } from 'react-router-dom';
import AddIcon from '@mui/icons-material/Add';
import { StreamsList } from '../components/streams/StreamsList';

export function StreamsPage() {
  const navigate = useNavigate();

  const handleCreateJob = () => {
    // Navigate to a temporary job ID for now
    // Later, this will create an actual job via API and navigate to it
    navigate('/streams/new-job');
  };

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="h4">Streams</Typography>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={handleCreateJob}
        >
          Create New Job
        </Button>
      </Box>

      <StreamsList />
    </Box>
  );
}
