import { memo } from 'react';
import { Handle, Position, useReactFlow, type NodeProps } from '@xyflow/react';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import FilterListIcon from '@mui/icons-material/FilterList';
import { BaseNode } from './BaseNode';
import type { FilterNodeType } from './index';

/** Node role determines handle configuration: sources have no input, sinks have no output */
export const filterRole = 'transform' as const;

export const FilterNode = memo(function FilterNode({ id, data }: NodeProps<FilterNodeType>) {
  const { updateNodeData } = useReactFlow();

  return (
    <>
      <Handle type="target" position={Position.Left} />
      <BaseNode
        nodeId={id}
        title="Filter"
        icon={<FilterListIcon fontSize="small" />}
        error={data.schemaError}
        isInferring={data.isInferring}
        outputSchema={data.outputSchema}
      >
        <TextField
          fullWidth
          size="small"
          label="Filter Expression"
          value={data.expression}
          onChange={(e) => updateNodeData(id, { expression: e.target.value })}
          className="nodrag"
          placeholder=".price > 50000"
          multiline
          minRows={1}
          maxRows={3}
        />
        <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, display: 'block' }}>
          Use .fieldName for fields (e.g., .price &gt; 100, .country == "US")
        </Typography>
      </BaseNode>
      <Handle type="source" position={Position.Right} />
    </>
  );
});
