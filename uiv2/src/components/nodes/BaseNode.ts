import { ClassicPreset } from 'rete';
import type { TypeStreamNode } from '../../types/rete';
import type { PipelineNode } from '../../generated/job_pb';

export abstract class BaseNode extends ClassicPreset.Node implements TypeStreamNode {
  abstract toProto(nodeId: string): PipelineNode;

  // Styling
  height = 180;
  width = 240;
}
