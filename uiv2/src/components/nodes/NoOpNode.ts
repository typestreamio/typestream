import { ClassicPreset } from 'rete';
import { BaseNode } from './BaseNode';
import { dataStreamSocket } from '../../types/rete';
import { PipelineNode, NoOpNode as NoOpNodeProto } from '../../generated/job_pb';

export class NoOpNode extends BaseNode {
  constructor() {
    super("No-Op");

    // Input socket
    this.addInput("in", new ClassicPreset.Input(dataStreamSocket, "Input"));

    // Output socket
    this.addOutput("out", new ClassicPreset.Output(dataStreamSocket, "Output"));
  }

  toProto(nodeId: string): PipelineNode {
    return new PipelineNode({
      id: nodeId,
      nodeType: {
        case: 'noop',
        value: new NoOpNodeProto()
      }
    });
  }
}
