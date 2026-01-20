import { useMemo, memo } from 'react';
import {
  ReactFlow,
  Background,
  Controls,
  type NodeTypes,
  Handle,
  Position,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import { useTheme } from '@mui/material/styles';
import TopicIcon from '@mui/icons-material/Topic';
import OutputIcon from '@mui/icons-material/Output';
import PublicIcon from '@mui/icons-material/Public';
import VisibilityIcon from '@mui/icons-material/Visibility';
import DataArrayIcon from '@mui/icons-material/DataArray';
import TextFieldsIcon from '@mui/icons-material/TextFields';
import SmartToyIcon from '@mui/icons-material/SmartToy';
import DataObjectIcon from '@mui/icons-material/DataObject';
import type { PipelineGraph } from '../../generated/job_pb';
import { deserializeGraph } from '../../utils/graphDeserializer';
import { edgeTypes } from './edges';

interface PipelineGraphViewerProps {
  graph: PipelineGraph;
  isRunning?: boolean;
  height?: number;
}

/**
 * Viewer node component - displays node info in read-only mode
 */
interface ViewerNodeData {
  label: string;
  topicPath?: string;
  topicName?: string;
  ipField?: string;
  outputField?: string;
  textField?: string;
  prompt?: string;
  model?: string;
  aggregationType?: string;
  groupByField?: string;
  filePathField?: string;
  [key: string]: unknown;
}

interface ViewerNodeProps {
  data: ViewerNodeData;
  type?: string;
}

const nodeIcons: Record<string, React.ReactNode> = {
  kafkaSource: <TopicIcon fontSize="small" sx={{ color: 'primary.main' }} />,
  kafkaSink: <OutputIcon fontSize="small" sx={{ color: 'success.main' }} />,
  geoIp: <PublicIcon fontSize="small" sx={{ color: 'info.main' }} />,
  inspector: <VisibilityIcon fontSize="small" sx={{ color: 'warning.main' }} />,
  materializedView: <DataArrayIcon fontSize="small" sx={{ color: 'secondary.main' }} />,
  textExtractor: <TextFieldsIcon fontSize="small" sx={{ color: 'info.main' }} />,
  embeddingGenerator: <DataObjectIcon fontSize="small" sx={{ color: 'info.main' }} />,
  openAiTransformer: <SmartToyIcon fontSize="small" sx={{ color: 'primary.main' }} />,
  default: <DataObjectIcon fontSize="small" sx={{ color: 'grey.500' }} />,
};

const nodeTitles: Record<string, string> = {
  kafkaSource: 'Kafka Source',
  kafkaSink: 'Kafka Sink',
  geoIp: 'GeoIP Enrichment',
  inspector: 'Inspector',
  materializedView: 'Materialized View',
  textExtractor: 'Text Extractor',
  embeddingGenerator: 'Embedding Generator',
  openAiTransformer: 'AI Transformer',
  default: 'Node',
};

const ViewerNode = memo(({ data, type = 'default' }: ViewerNodeProps) => {
  const theme = useTheme();
  const isSource = type === 'kafkaSource';
  const isSink = type === 'kafkaSink';

  // Get display value based on node type
  const displayValue = useMemo(() => {
    if (data.topicPath) return data.topicPath;
    if (data.topicName) return data.topicName;
    if (data.ipField) return `IP: ${data.ipField} -> ${data.outputField}`;
    if (data.prompt) return data.prompt.substring(0, 50) + (data.prompt.length > 50 ? '...' : '');
    if (data.textField) return `Text: ${data.textField}`;
    if (data.filePathField) return `File: ${data.filePathField}`;
    if (data.groupByField) return `Group by: ${data.groupByField}`;
    return data.label || '';
  }, [data]);

  return (
    <>
      {/* Input handle (hidden for sources) */}
      {!isSource && (
        <Handle
          type="target"
          position={Position.Left}
          style={{ background: theme.palette.primary.main }}
        />
      )}

      <Paper
        elevation={2}
        sx={{
          minWidth: 180,
          maxWidth: 250,
          bgcolor: 'background.paper',
          border: '1px solid',
          borderColor: 'divider',
        }}
      >
        <Box
          sx={{
            px: 1.5,
            py: 0.75,
            borderBottom: '1px solid',
            borderColor: 'divider',
            bgcolor: 'action.hover',
            display: 'flex',
            alignItems: 'center',
            gap: 1,
          }}
        >
          {nodeIcons[type] || nodeIcons.default}
          <Typography variant="caption" fontWeight="bold">
            {nodeTitles[type] || nodeTitles.default}
          </Typography>
        </Box>
        <Box sx={{ px: 1.5, py: 1 }}>
          <Typography
            variant="body2"
            sx={{
              fontFamily: 'monospace',
              fontSize: '0.75rem',
              color: 'text.secondary',
              wordBreak: 'break-word',
            }}
          >
            {displayValue}
          </Typography>
        </Box>
      </Paper>

      {/* Output handle (hidden for sinks) */}
      {!isSink && (
        <Handle
          type="source"
          position={Position.Right}
          style={{ background: theme.palette.primary.main }}
        />
      )}
    </>
  );
});

ViewerNode.displayName = 'ViewerNode';

// Create node type mappings for the viewer
const viewerNodeTypes: NodeTypes = {
  kafkaSource: (props: { data: ViewerNodeData }) => <ViewerNode {...props} type="kafkaSource" />,
  kafkaSink: (props: { data: ViewerNodeData }) => <ViewerNode {...props} type="kafkaSink" />,
  geoIp: (props: { data: ViewerNodeData }) => <ViewerNode {...props} type="geoIp" />,
  inspector: (props: { data: ViewerNodeData }) => <ViewerNode {...props} type="inspector" />,
  materializedView: (props: { data: ViewerNodeData }) => <ViewerNode {...props} type="materializedView" />,
  textExtractor: (props: { data: ViewerNodeData }) => <ViewerNode {...props} type="textExtractor" />,
  embeddingGenerator: (props: { data: ViewerNodeData }) => <ViewerNode {...props} type="embeddingGenerator" />,
  openAiTransformer: (props: { data: ViewerNodeData }) => <ViewerNode {...props} type="openAiTransformer" />,
  default: (props: { data: ViewerNodeData }) => <ViewerNode {...props} type="default" />,
};

/**
 * PipelineGraphViewer displays a read-only visualization of a PipelineGraph.
 * Used on the Job Detail page to show the job's pipeline structure.
 */
export function PipelineGraphViewer({
  graph,
  isRunning = false,
  height = 350,
}: PipelineGraphViewerProps) {
  const theme = useTheme();

  // Deserialize and layout the graph
  const { nodes, edges } = useMemo(() => {
    const result = deserializeGraph(graph);

    // If running, make all edges animated
    if (isRunning) {
      return {
        nodes: result.nodes,
        edges: result.edges.map((edge) => ({
          ...edge,
          type: 'animated',
        })),
      };
    }

    return result;
  }, [graph, isRunning]);

  return (
    <Box
      sx={{
        height,
        border: '1px solid',
        borderColor: 'divider',
        borderRadius: 1,
        overflow: 'hidden',
      }}
    >
      <ReactFlow
        nodes={nodes}
        edges={edges}
        nodeTypes={viewerNodeTypes}
        edgeTypes={edgeTypes}
        colorMode="light"
        fitView
        fitViewOptions={{ padding: 0.2 }}
        // Disable all interactions for viewer mode
        nodesDraggable={false}
        nodesConnectable={false}
        elementsSelectable={false}
        panOnDrag={true}
        zoomOnScroll={true}
        preventScrolling={true}
      >
        <Background color={theme.palette.primary.main} gap={16} />
        <Controls showInteractive={false} />
      </ReactFlow>
    </Box>
  );
}
