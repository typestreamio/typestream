import { Handle, Position, useReactFlow, type NodeProps } from '@xyflow/react';
import TextField from '@mui/material/TextField';
import MenuItem from '@mui/material/MenuItem';
import Box from '@mui/material/Box';
import StorageIcon from '@mui/icons-material/Storage';
import { BaseNode } from './BaseNode';
import type { JDBCSinkNodeType } from './index';

export function JDBCSinkNode({ id, data }: NodeProps<JDBCSinkNodeType>) {
  const { updateNodeData } = useReactFlow();

  return (
    <>
      <Handle type="target" position={Position.Left} />
      <BaseNode title="JDBC Sink" icon={<StorageIcon fontSize="small" />}>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
          <TextField
            select
            fullWidth
            size="small"
            label="Database Type"
            value={data.databaseType || 'postgres'}
            onChange={(e) => updateNodeData(id, { databaseType: e.target.value })}
            className="nodrag nowheel"
          >
            <MenuItem value="postgres">PostgreSQL</MenuItem>
            <MenuItem value="mysql">MySQL</MenuItem>
          </TextField>
          <TextField
            fullWidth
            size="small"
            label="Hostname"
            value={data.hostname || ''}
            onChange={(e) => updateNodeData(id, { hostname: e.target.value })}
            placeholder="localhost"
            className="nodrag nowheel"
          />
          <TextField
            fullWidth
            size="small"
            label="Port"
            value={data.port || ''}
            onChange={(e) => updateNodeData(id, { port: e.target.value })}
            placeholder="5432"
            className="nodrag nowheel"
          />
          <TextField
            fullWidth
            size="small"
            label="Database"
            value={data.database || ''}
            onChange={(e) => updateNodeData(id, { database: e.target.value })}
            placeholder="mydb"
            className="nodrag nowheel"
          />
          <TextField
            fullWidth
            size="small"
            label="Username"
            value={data.username || ''}
            onChange={(e) => updateNodeData(id, { username: e.target.value })}
            placeholder="user"
            className="nodrag nowheel"
          />
          <TextField
            fullWidth
            size="small"
            label="Password"
            type="password"
            value={data.password || ''}
            onChange={(e) => updateNodeData(id, { password: e.target.value })}
            placeholder="password"
            className="nodrag nowheel"
          />
          <TextField
            fullWidth
            size="small"
            label="Table Name"
            value={data.tableName || ''}
            onChange={(e) => updateNodeData(id, { tableName: e.target.value })}
            placeholder="target_table"
            className="nodrag nowheel"
          />
          <TextField
            select
            fullWidth
            size="small"
            label="Insert Mode"
            value={data.insertMode || 'upsert'}
            onChange={(e) => updateNodeData(id, { insertMode: e.target.value })}
            className="nodrag nowheel"
          >
            <MenuItem value="insert">Insert</MenuItem>
            <MenuItem value="upsert">Upsert</MenuItem>
            <MenuItem value="update">Update</MenuItem>
          </TextField>
          <TextField
            fullWidth
            size="small"
            label="Primary Key Fields"
            value={data.primaryKeyFields || ''}
            onChange={(e) => updateNodeData(id, { primaryKeyFields: e.target.value })}
            placeholder="id (comma-separated)"
            helperText="Required for upsert/update mode"
            className="nodrag nowheel"
          />
        </Box>
      </BaseNode>
    </>
  );
}
