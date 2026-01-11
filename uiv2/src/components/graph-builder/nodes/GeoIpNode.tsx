import { Handle, Position, useReactFlow, type NodeProps } from '@xyflow/react';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import TextField from '@mui/material/TextField';
import PublicIcon from '@mui/icons-material/Public';
import { BaseNode } from './BaseNode';
import type { GeoIpNodeType } from './index';

export function GeoIpNode({ id, data }: NodeProps<GeoIpNodeType>) {
  const { updateNodeData } = useReactFlow();

  return (
    <>
      <Handle type="target" position={Position.Left} />
      <BaseNode title="GeoIP Lookup" icon={<PublicIcon fontSize="small" />}>
        <TextField
          fullWidth
          size="small"
          label="IP Field"
          value={data.ipField}
          onChange={(e) => updateNodeData(id, { ipField: e.target.value })}
          placeholder="ip_address"
          className="nodrag nowheel"
          helperText="Field containing the IP address"
        />
        <TextField
          fullWidth
          size="small"
          label="Output Field"
          value={data.outputField}
          onChange={(e) => updateNodeData(id, { outputField: e.target.value })}
          placeholder="country_code"
          className="nodrag nowheel"
          sx={{ mt: 1.5 }}
          helperText="Field name for the country code"
        />
      </BaseNode>
      <Handle type="source" position={Position.Right} />
    </>
  );
}
