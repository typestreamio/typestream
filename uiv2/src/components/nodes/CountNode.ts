import { ClassicPreset } from 'rete';
import { BaseNode } from './BaseNode';
import { dataStreamSocket } from '../../types/rete';
import { PipelineNode, CountNode as CountNodeProto } from '../../generated/job_pb';

export class CountNode extends BaseNode {
  constructor() {
    super("Count");

    // Input socket
    this.addInput("in", new ClassicPreset.Input(dataStreamSocket, "Input"));

    // Output socket
    this.addOutput("out", new ClassicPreset.Output(dataStreamSocket, "Count"));
  }

  toProto(nodeId: string): PipelineNode {
    return new PipelineNode({
      id: nodeId,
      nodeType: {
        case: 'count',
        value: new CountNodeProto()
      }
    });
  }
}
