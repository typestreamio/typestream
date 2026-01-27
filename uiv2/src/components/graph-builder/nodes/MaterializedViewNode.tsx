import { memo, useEffect, useMemo } from 'react';
import { Handle, Position, useReactFlow, useNodes, useEdges, type NodeProps } from '@xyflow/react';
import Box from '@mui/material/Box';
import Checkbox from '@mui/material/Checkbox';
import FormControl from '@mui/material/FormControl';
import FormControlLabel from '@mui/material/FormControlLabel';
import InputLabel from '@mui/material/InputLabel';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import TableChartIcon from '@mui/icons-material/TableChart';
import { BaseNode } from './BaseNode';
import type { MaterializedViewNodeType, NodeValidationState } from './index';

export type AggregationType = 'count' | 'latest';

/** Node role determines handle configuration: sources have no input, sinks have no output */
export const materializedViewRole = 'sink' as const;

export const MaterializedViewNode = memo(function MaterializedViewNode({ id, data }: NodeProps<MaterializedViewNodeType>) {
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

  // Auto-select first field when schema loads and no field is selected
  useEffect(() => {
    if (!data.groupByField && fields.length > 0) {
      updateNodeData(id, { groupByField: fields[0].name });
    }
  }, [data.groupByField, fields, id, updateNodeData]);

  return (
    <>
      <Handle type="target" position={Position.Left} />
      <BaseNode
        nodeId={id}
        title="Materialized View"
        icon={<TableChartIcon fontSize="small" />}
        error={data.schemaError}
        isInferring={data.isInferring}
        outputSchema={data.outputSchema}
      >
        <FormControl fullWidth size="small" className="nodrag nowheel" sx={{ mb: 1.5 }}>
          <InputLabel>Aggregation</InputLabel>
          <Select
            value={data.aggregationType}
            label="Aggregation"
            onChange={(e) => updateNodeData(id, { aggregationType: e.target.value })}
          >
            <MenuItem value="count">Count</MenuItem>
            <MenuItem value="latest">Latest Value</MenuItem>
          </Select>
        </FormControl>
        <FormControl fullWidth size="small" className="nodrag nowheel">
          <InputLabel>Group By Field</InputLabel>
          <Select
            value={data.groupByField}
            label="Group By Field"
            onChange={(e) => updateNodeData(id, { groupByField: e.target.value })}
            disabled={data.isInferring || fields.length === 0}
          >
            {fields.map((field) => (
              <MenuItem key={field.name} value={field.name}>
                {field.name}
                <Typography component="span" color="text.secondary" sx={{ ml: 1, fontSize: '0.75rem' }}>
                  ({field.type})
                </Typography>
              </MenuItem>
            ))}
          </Select>
        </FormControl>

        {data.aggregationType === 'count' && (
          <Box sx={{ mt: 1.5 }}>
            <FormControlLabel
              className="nodrag"
              control={
                <Checkbox
                  size="small"
                  checked={data.enableWindowing ?? false}
                  onChange={(e) => updateNodeData(id, {
                    enableWindowing: e.target.checked,
                    windowSizeSeconds: e.target.checked ? (data.windowSizeSeconds ?? 60) : undefined
                  })}
                />
              }
              label={<Typography variant="body2">Enable Windowing</Typography>}
            />
            {data.enableWindowing && (
              <TextField
                className="nodrag"
                fullWidth
                size="small"
                type="number"
                label="Window Size (seconds)"
                value={data.windowSizeSeconds ?? 60}
                onChange={(e) => updateNodeData(id, { windowSizeSeconds: parseInt(e.target.value) || 60 })}
                inputProps={{ min: 1 }}
                sx={{ mt: 1 }}
              />
            )}
          </Box>
        )}
      </BaseNode>
      {/* No output handle - this is a terminal node */}
    </>
  );
});
