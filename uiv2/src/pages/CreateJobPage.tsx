import { Box, Typography, Breadcrumbs, Link, Paper } from '@mui/material';
import { useNavigate } from 'react-router-dom';
import { ReteEditor } from '../components/ReteEditor';

export function CreateJobPage() {
  const navigate = useNavigate();

  const handleJobCreated = (jobId: string) => {
    // Navigate to the job detail page after creation
    navigate(`/streams/${jobId}`);
  };

  return (
    <Box>
      {/* Breadcrumbs */}
      <Breadcrumbs sx={{ mb: 3 }}>
        <Link
          component="button"
          variant="body1"
          onClick={() => navigate('/streams')}
          sx={{ cursor: 'pointer', textDecoration: 'none', color: 'primary.main' }}
        >
          Streams
        </Link>
        <Typography color="text.primary">Create New Job</Typography>
      </Breadcrumbs>

      {/* Page Title */}
      <Typography variant="h4" sx={{ mb: 3 }}>
        Create New Stream Pipeline
      </Typography>

      {/* Rete Editor */}
      <Paper elevation={2} sx={{ p: 2 }}>
        <ReteEditor onJobCreated={handleJobCreated} />
      </Paper>
    </Box>
  );
}
