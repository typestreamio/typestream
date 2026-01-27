import { memo } from 'react';
import { Handle, Position, useReactFlow, type NodeProps } from '@xyflow/react';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import Chip from '@mui/material/Chip';
import Box from '@mui/material/Box';
import StorageIcon from '@mui/icons-material/Storage';
import { BaseNode } from './BaseNode';
import { usePostgresTables } from '../../../hooks/usePostgresTables';
import type { PostgresSourceNodeType } from './index';

/** Node role determines handle configuration: sources have no input, sinks have no output */
export const postgresSourceRole = 'source' as const;

export const PostgresSourceNode = memo(function PostgresSourceNode({ id, data }: NodeProps<PostgresSourceNodeType>) {
  const { tables } = usePostgresTables();
  const { updateNodeData } = useReactFlow();

  // Find the selected table
  const selectedTable = tables.find((t) => t.topicPath === data.topicPath);

  // Display title: connection name if available, otherwise "Postgres Source"
  const title = data.connectionName || 'Postgres Source';

  return (
    <>
      <BaseNode
        nodeId={id}
        title={title}
        icon={<StorageIcon fontSize="small" color="primary" />}
        error={data.schemaError}
        isInferring={data.isInferring}
        outputSchema={data.outputSchema}
      >
        <FormControl fullWidth size="small" className="nodrag nowheel">
          <InputLabel>Table</InputLabel>
          <Select
            value={data.topicPath}
            label="Table"
            onChange={(e) => {
              const table = tables.find((t) => t.topicPath === e.target.value);
              if (table) {
                updateNodeData(id, {
                  topicPath: table.topicPath,
                  tableName: table.table,
                  schemaName: table.schema,
                });
              }
            }}
          >
            {tables.map((table) => (
              <MenuItem key={table.topicPath} value={table.topicPath}>
                {table.schema}.{table.table}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
        {selectedTable && (
          <Box sx={{ mt: 1, display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
            <Chip
              label={selectedTable.schema}
              size="small"
              color="default"
              variant="outlined"
            />
            <Chip
              label="CDC"
              size="small"
              color="primary"
              variant="outlined"
            />
          </Box>
        )}
      </BaseNode>
      <Handle type="source" position={Position.Right} />
    </>
  );
});
