import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import Divider from '@mui/material/Divider';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import InputIcon from '@mui/icons-material/Input';
import OutputIcon from '@mui/icons-material/Output';
import PublicIcon from '@mui/icons-material/Public';
import VisibilityIcon from '@mui/icons-material/Visibility';
import TableChartIcon from '@mui/icons-material/TableChart';
import DescriptionIcon from '@mui/icons-material/Description';
import MemoryIcon from '@mui/icons-material/Memory';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import StorageIcon from '@mui/icons-material/Storage';
import AddIcon from '@mui/icons-material/Add';
import type { DragEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useSinkConnections, type Connection } from '../../hooks/useConnections';

interface PaletteItemProps {
  type: string;
  label: string;
  icon: React.ReactNode;
  data?: Record<string, unknown>;  // Additional data to pass with drag
  onAdd?: (type: string, data?: Record<string, unknown>) => void;
}

function PaletteItem({ type, label, icon, data, onAdd }: PaletteItemProps) {
  const onDragStart = (event: DragEvent) => {
    // Encode type and any additional data
    const payload = data ? JSON.stringify({ type, ...data }) : type;
    event.dataTransfer.setData('application/reactflow', payload);
    event.dataTransfer.effectAllowed = 'move';
  };

  const handleClick = () => {
    if (onAdd) {
      onAdd(type, data);
    }
  };

  return (
    <Paper
      elevation={1}
      draggable
      onDragStart={onDragStart}
      onClick={handleClick}
      sx={{
        p: 1.5,
        cursor: 'pointer',
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
      <Typography variant="body2" noWrap sx={{ flex: 1 }}>{label}</Typography>
      <AddIcon fontSize="small" sx={{ color: 'text.secondary', opacity: 0.6 }} />
    </Paper>
  );
}

function ConnectionSinkItem({ connection, onAdd }: { connection: Connection; onAdd?: (type: string, data?: Record<string, unknown>) => void }) {
  return (
    <PaletteItem
      type="dbSink"
      label={connection.name}
      icon={<StorageIcon fontSize="small" color={connection.databaseType === 'postgres' ? 'primary' : 'secondary'} />}
      data={{
        // Only pass non-sensitive data - credentials stay server-side
        connectionId: connection.id,
        connectionName: connection.name,
        databaseType: connection.databaseType,
      }}
      onAdd={onAdd}
    />
  );
}

interface NodePaletteProps {
  onAddNode?: (type: string, data?: Record<string, unknown>) => void;
}

export function NodePalette({ onAddNode }: NodePaletteProps) {
  const navigate = useNavigate();
  const { data: connections } = useSinkConnections();

  return (
    <Paper
      elevation={2}
      sx={{
        width: 240,
        p: 2,
        display: 'flex',
        flexDirection: 'column',
        gap: 1,
        overflow: 'auto',
      }}
    >
      <Typography variant="subtitle2" color="text.secondary">
        Sources
      </Typography>
      <PaletteItem
        type="kafkaSource"
        label="Kafka Source"
        icon={<InputIcon fontSize="small" />}
        onAdd={onAddNode}
      />

      <Divider sx={{ my: 1 }} />

      <Typography variant="subtitle2" color="text.secondary">
        Transforms
      </Typography>
      <PaletteItem
        type="geoIp"
        label="GeoIP Lookup"
        icon={<PublicIcon fontSize="small" />}
        onAdd={onAddNode}
      />
      <PaletteItem
        type="textExtractor"
        label="Text Extractor"
        icon={<DescriptionIcon fontSize="small" />}
        onAdd={onAddNode}
      />
      <PaletteItem
        type="embeddingGenerator"
        label="Embedding Generator"
        icon={<MemoryIcon fontSize="small" />}
        onAdd={onAddNode}
      />
      <PaletteItem
        type="openAiTransformer"
        label="OpenAI Transformer"
        icon={<AutoAwesomeIcon fontSize="small" />}
        onAdd={onAddNode}
      />

      <Divider sx={{ my: 1 }} />

      <Typography variant="subtitle2" color="text.secondary">
        Sinks
      </Typography>
      <PaletteItem
        type="kafkaSink"
        label="Kafka Sink"
        icon={<OutputIcon fontSize="small" />}
        onAdd={onAddNode}
      />
      <PaletteItem
        type="inspector"
        label="Inspector"
        icon={<VisibilityIcon fontSize="small" />}
        onAdd={onAddNode}
      />
      <PaletteItem
        type="materializedView"
        label="Materialized View"
        icon={<TableChartIcon fontSize="small" />}
        onAdd={onAddNode}
      />

      <Divider sx={{ my: 1 }} />

      <Typography variant="subtitle2" color="text.secondary">
        Database Sinks
      </Typography>
      {connections && connections.length > 0 ? (
        connections.map((conn) => (
          <ConnectionSinkItem key={conn.id} connection={conn} onAdd={onAddNode} />
        ))
      ) : (
        <Box sx={{ py: 1 }}>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
            No connections configured
          </Typography>
          <Button
            size="small"
            variant="outlined"
            startIcon={<AddIcon />}
            onClick={() => navigate('/connections/new')}
            fullWidth
          >
            Add Connection
          </Button>
        </Box>
      )}
    </Paper>
  );
}
