import { memo, useEffect } from 'react';
import { Handle, Position, useReactFlow, useNodes, useEdges, type NodeProps } from '@xyflow/react';
import TextField from '@mui/material/TextField';
import MenuItem from '@mui/material/MenuItem';
import Autocomplete from '@mui/material/Autocomplete';
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';
import HubIcon from '@mui/icons-material/Hub';
import { BaseNode } from './BaseNode';
import type { WeaviateSinkNodeType, NodeValidationState, SchemaField } from './index';

/** Node role determines handle configuration: sources have no input, sinks have no output */
export const weaviateSinkRole = 'sink' as const;

export const WeaviateSinkNode = memo(function WeaviateSinkNode({ id, data }: NodeProps<WeaviateSinkNodeType>) {
  const { updateNodeData } = useReactFlow();
  const nodes = useNodes();
  const edges = useEdges();

  // Find the upstream node to get its output schema
  const incomingEdge = edges.find((e) => e.target === id);
  const upstreamNode = incomingEdge
    ? nodes.find((n) => n.id === incomingEdge.source)
    : null;

  // Get fields from upstream node's computed output schema
  const upstreamData = upstreamNode?.data as NodeValidationState | undefined;
  const fields = upstreamData?.outputSchema ?? [];

  // Filter to array fields for vector field selection
  const arrayFields = fields.filter((f) => f.type.includes('Array') || f.type.includes('[]'));

  // Auto-select embedding field when schema loads and no vector field is set
  useEffect(() => {
    if (arrayFields.length > 0 && !data.vectorField && data.vectorStrategy === 'FieldVectorStrategy') {
      // Look for common embedding field names
      const embeddingField = arrayFields.find((f) =>
        f.name === 'embedding' || f.name === 'embeddings' || f.name === 'vector'
      );
      if (embeddingField) {
        updateNodeData(id, { vectorField: embeddingField.name });
      }
    }
  }, [arrayFields, data.vectorField, data.vectorStrategy, id, updateNodeData]);

  return (
    <>
      <Handle type="target" position={Position.Left} />
      <BaseNode
        nodeId={id}
        title={data.connectionName}
        icon={<HubIcon fontSize="small" />}
        error={data.schemaError}
        isInferring={data.isInferring}
        outputSchema={data.outputSchema}
      >
        <Typography variant="caption" color="text.secondary" sx={{ mb: 1 }}>
          Weaviate Vector DB
        </Typography>
        <TextField
          fullWidth
          size="small"
          label="Collection Name"
          value={data.collectionName}
          onChange={(e) => updateNodeData(id, { collectionName: e.target.value })}
          placeholder="Documents"
          className="nodrag nowheel"
          sx={{ mb: 1 }}
        />
        <TextField
          fullWidth
          size="small"
          select
          label="Document ID Strategy"
          value={data.documentIdStrategy}
          onChange={(e) => updateNodeData(id, { documentIdStrategy: e.target.value })}
          className="nodrag nowheel"
          sx={{ mb: 1 }}
        >
          <MenuItem value="NoIdStrategy">Auto-generate UUID</MenuItem>
          <MenuItem value="KafkaIdStrategy">Use Kafka Key</MenuItem>
          <MenuItem value="FieldIdStrategy">Use Field Value</MenuItem>
        </TextField>
        {data.documentIdStrategy === 'FieldIdStrategy' && (
          <Autocomplete
            freeSolo
            size="small"
            options={fields}
            getOptionLabel={(option) => typeof option === 'string' ? option : option.name}
            value={data.documentIdField || ''}
            onChange={(_, newValue) => {
              const fieldName = typeof newValue === 'string' ? newValue : (newValue as SchemaField)?.name || '';
              updateNodeData(id, { documentIdField: fieldName });
            }}
            onInputChange={(_, newValue) => updateNodeData(id, { documentIdField: newValue })}
            disabled={data.isInferring}
            className="nodrag nowheel"
            sx={{ mb: 1 }}
            renderOption={(props, option) => {
              const field = option as SchemaField;
              return (
                <Box component="li" {...props}>
                  {field.name}
                  <Typography component="span" color="text.secondary" sx={{ ml: 1, fontSize: '0.75rem' }}>
                    ({field.type})
                  </Typography>
                </Box>
              );
            }}
            renderInput={(params) => (
              <TextField
                {...params}
                label="ID Field"
              />
            )}
          />
        )}
        <TextField
          fullWidth
          size="small"
          select
          label="Vector Strategy"
          value={data.vectorStrategy}
          onChange={(e) => updateNodeData(id, { vectorStrategy: e.target.value })}
          className="nodrag nowheel"
          sx={{ mb: 1 }}
        >
          <MenuItem value="NoVectorStrategy">No Vectors (Weaviate vectorizes)</MenuItem>
          <MenuItem value="FieldVectorStrategy">Use Existing Embedding</MenuItem>
        </TextField>
        {data.vectorStrategy === 'FieldVectorStrategy' && (
          <Autocomplete
            freeSolo
            size="small"
            options={arrayFields.length > 0 ? arrayFields : fields}
            getOptionLabel={(option) => typeof option === 'string' ? option : option.name}
            value={data.vectorField || ''}
            onChange={(_, newValue) => {
              const fieldName = typeof newValue === 'string' ? newValue : (newValue as SchemaField)?.name || '';
              updateNodeData(id, { vectorField: fieldName });
            }}
            onInputChange={(_, newValue) => updateNodeData(id, { vectorField: newValue })}
            disabled={data.isInferring}
            className="nodrag nowheel"
            renderOption={(props, option) => {
              const field = option as SchemaField;
              return (
                <Box component="li" {...props}>
                  {field.name}
                  <Typography component="span" color="text.secondary" sx={{ ml: 1, fontSize: '0.75rem' }}>
                    ({field.type})
                  </Typography>
                </Box>
              );
            }}
            renderInput={(params) => (
              <TextField
                {...params}
                label="Embedding Field"
                helperText={arrayFields.length === 0 && fields.length > 0 ? "No array fields found" : undefined}
              />
            )}
          />
        )}
      </BaseNode>
    </>
  );
});
