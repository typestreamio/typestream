import { memo, useEffect, useMemo } from 'react';
import { Handle, Position, useReactFlow, useNodes, useEdges, type NodeProps } from '@xyflow/react';
import TextField from '@mui/material/TextField';
import MenuItem from '@mui/material/MenuItem';
import Autocomplete from '@mui/material/Autocomplete';
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';
import StorageIcon from '@mui/icons-material/Storage';
import { BaseNode } from './BaseNode';
import type { DbSinkNodeType, NodeValidationState, SchemaField } from './index';

/** Node role determines handle configuration: sources have no input, sinks have no output */
export const dbSinkRole = 'sink' as const;

export const DbSinkNode = memo(function DbSinkNode({ id, data }: NodeProps<DbSinkNodeType>) {
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
  const fields = useMemo(
    () => upstreamData?.outputSchema ?? [],
    [upstreamData?.outputSchema]
  );

  // Auto-select "id" as primary key when schema loads and no key is set
  useEffect(() => {
    if (fields.length > 0 && !data.primaryKeyFields) {
      // Look for common primary key field names
      const idField = fields.find((f) => f.name === 'id' || f.name === 'ID' || f.name === 'Id');
      if (idField) {
        updateNodeData(id, { primaryKeyFields: idField.name });
      }
    }
  }, [fields, data.primaryKeyFields, id, updateNodeData]);

  return (
    <>
      <Handle type="target" position={Position.Left} />
      <BaseNode
        nodeId={id}
        title={data.connectionName}
        icon={<StorageIcon fontSize="small" />}
        error={data.schemaError}
        isInferring={data.isInferring}
        outputSchema={data.outputSchema}
      >
        <Typography variant="caption" color="text.secondary" sx={{ mb: 1 }}>
          {data.databaseType?.toUpperCase()} Sink
        </Typography>
        <TextField
          fullWidth
          size="small"
          label="Table Name"
          value={data.tableName}
          onChange={(e) => updateNodeData(id, { tableName: e.target.value })}
          placeholder="users_sink"
          className="nodrag nowheel"
          sx={{ mb: 1 }}
        />
        <TextField
          fullWidth
          size="small"
          select
          label="Insert Mode"
          value={data.insertMode}
          onChange={(e) => updateNodeData(id, { insertMode: e.target.value })}
          className="nodrag nowheel"
          sx={{ mb: 1 }}
        >
          <MenuItem value="insert">Insert</MenuItem>
          <MenuItem value="upsert">Upsert</MenuItem>
          <MenuItem value="update">Update</MenuItem>
        </TextField>
        {(data.insertMode === 'upsert' || data.insertMode === 'update') && (
          <Autocomplete
            freeSolo
            size="small"
            options={fields}
            getOptionLabel={(option) => typeof option === 'string' ? option : option.name}
            value={data.primaryKeyFields || ''}
            onChange={(_, newValue) => {
              const fieldName = typeof newValue === 'string' ? newValue : (newValue as SchemaField)?.name || '';
              updateNodeData(id, { primaryKeyFields: fieldName });
            }}
            onInputChange={(_, newValue) => updateNodeData(id, { primaryKeyFields: newValue })}
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
                label="Primary Key"
              />
            )}
          />
        )}
      </BaseNode>
    </>
  );
});
