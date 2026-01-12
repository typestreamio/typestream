import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import InputIcon from '@mui/icons-material/Input';
import OutputIcon from '@mui/icons-material/Output';
import PublicIcon from '@mui/icons-material/Public';
import type { DragEvent } from 'react';

interface PaletteItemProps {
  type: string;
  label: string;
  icon: React.ReactNode;
}

function PaletteItem({ type, label, icon }: PaletteItemProps) {
  const onDragStart = (event: DragEvent) => {
    event.dataTransfer.setData('application/reactflow', type);
    event.dataTransfer.effectAllowed = 'move';
  };

  return (
    <Paper
      elevation={1}
      draggable
      onDragStart={onDragStart}
      sx={{
        p: 1.5,
        cursor: 'grab',
        display: 'flex',
        alignItems: 'center',
        gap: 1,
        '&:hover': {
          bgcolor: 'action.hover',
        },
        '&:active': {
          cursor: 'grabbing',
        },
      }}
    >
      {icon}
      <Typography variant="body2">{label}</Typography>
    </Paper>
  );
}

export function NodePalette() {
  return (
    <Paper
      elevation={2}
      sx={{
        width: 200,
        p: 2,
        display: 'flex',
        flexDirection: 'column',
        gap: 1,
      }}
    >
      <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 1 }}>
        Drag nodes to canvas
      </Typography>
      <PaletteItem
        type="kafkaSource"
        label="Kafka Source"
        icon={<InputIcon fontSize="small" />}
      />
      <PaletteItem
        type="kafkaSink"
        label="Kafka Sink"
        icon={<OutputIcon fontSize="small" />}
      />
    </Paper>
  );
}
