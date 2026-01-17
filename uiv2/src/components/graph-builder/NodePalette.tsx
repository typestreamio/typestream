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
}

function PaletteItem({ type, label, icon, data }: PaletteItemProps) {
  const onDragStart = (event: DragEvent) => {
    // Encode type and any additional data
    const payload = data ? JSON.stringify({ type, ...data }) : type;
    event.dataTransfer.setData('application/reactflow', payload);
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
      <Typography variant="body2" noWrap>{label}</Typography>
    </Paper>
  );
}

function ConnectionSinkItem({ connection }: { connection: Connection }) {
  return (
    <PaletteItem
      type="dbSink"
      label={connection.name}
      icon={<StorageIcon fontSize="small" color={connection.databaseType === 'postgres' ? 'primary' : 'secondary'} />}
      data={{
        connectionId: connection.id,
        connectionName: connection.name,
        databaseType: connection.databaseType,
        // Include full connection config for JDBC sink creation
        hostname: connection.hostname,
        connectorHostname: connection.connectorHostname,  // Docker network hostname
        port: connection.port,
        database: connection.database,
        username: connection.username,
        password: connection.password,
      }}
    />
  );
}

export function NodePalette() {
  const navigate = useNavigate();
  const { data: connections } = useSinkConnections();

  return (
    <Paper
      elevation={2}
      sx={{
        width: 200,
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
      />

      <Divider sx={{ my: 1 }} />

      <Typography variant="subtitle2" color="text.secondary">
        Transforms
      </Typography>
      <PaletteItem
        type="geoIp"
        label="GeoIP Lookup"
        icon={<PublicIcon fontSize="small" />}
      />
      <PaletteItem
        type="textExtractor"
        label="Text Extractor"
        icon={<DescriptionIcon fontSize="small" />}
      />
      <PaletteItem
        type="embeddingGenerator"
        label="Embedding Generator"
        icon={<MemoryIcon fontSize="small" />}
      />
      <PaletteItem
        type="openAiTransformer"
        label="OpenAI Transformer"
        icon={<AutoAwesomeIcon fontSize="small" />}
      />

      <Divider sx={{ my: 1 }} />

      <Typography variant="subtitle2" color="text.secondary">
        Sinks
      </Typography>
      <PaletteItem
        type="kafkaSink"
        label="Kafka Sink"
        icon={<OutputIcon fontSize="small" />}
      />
      <PaletteItem
        type="inspector"
        label="Inspector"
        icon={<VisibilityIcon fontSize="small" />}
      />
      <PaletteItem
        type="materializedView"
        label="Materialized View"
        icon={<TableChartIcon fontSize="small" />}
      />

      <Divider sx={{ my: 1 }} />

      <Typography variant="subtitle2" color="text.secondary">
        Database Sinks
      </Typography>
      {connections && connections.length > 0 ? (
        connections.map((conn) => (
          <ConnectionSinkItem key={conn.id} connection={conn} />
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
