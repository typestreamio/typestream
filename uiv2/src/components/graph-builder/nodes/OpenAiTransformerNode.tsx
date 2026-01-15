import { Handle, Position, useReactFlow, type NodeProps } from '@xyflow/react';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import TextField from '@mui/material/TextField';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import { BaseNode } from './BaseNode';
import { useListOpenAIModels } from '../../../hooks/useListOpenAIModels';
import type { OpenAiTransformerNodeType } from './index';

export function OpenAiTransformerNode({ id, data }: NodeProps<OpenAiTransformerNodeType>) {
  const { updateNodeData } = useReactFlow();
  const { data: modelsResponse } = useListOpenAIModels();
  const models = modelsResponse?.models ?? [];

  return (
    <>
      <Handle type="target" position={Position.Left} />
      <BaseNode
        title="OpenAI Transformer"
        icon={<AutoAwesomeIcon fontSize="small" />}
        error={data.schemaError}
        isInferring={data.isInferring}
      >
        <FormControl fullWidth size="small" className="nodrag nowheel" sx={{ mb: 1.5 }}>
          <InputLabel>Model</InputLabel>
          <Select
            value={data.model}
            label="Model"
            onChange={(e) => updateNodeData(id, { model: e.target.value })}
          >
            {models.length > 0 ? (
              models.map((m) => (
                <MenuItem key={m.id} value={m.id}>
                  {m.name}
                </MenuItem>
              ))
            ) : (
              <>
                <MenuItem value="gpt-4o-mini">gpt-4o-mini</MenuItem>
                <MenuItem value="gpt-4o">gpt-4o</MenuItem>
                <MenuItem value="gpt-3.5-turbo">gpt-3.5-turbo</MenuItem>
              </>
            )}
          </Select>
        </FormControl>
        <TextField
          fullWidth
          size="small"
          label="Prompt"
          value={data.prompt}
          onChange={(e) => updateNodeData(id, { prompt: e.target.value })}
          className="nodrag"
          placeholder="Categorize this message..."
          multiline
          rows={3}
          sx={{ mb: 1.5 }}
        />
        <TextField
          fullWidth
          size="small"
          label="Output Field"
          value={data.outputField}
          onChange={(e) => updateNodeData(id, { outputField: e.target.value })}
          className="nodrag"
          placeholder="ai_response"
        />
      </BaseNode>
      <Handle type="source" position={Position.Right} />
    </>
  );
}
