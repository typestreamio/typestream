import { ClassicPreset } from 'rete';
import { BaseNode } from './BaseNode';
import { dataStreamSocket } from '../../types/rete';
import { PipelineNode } from '../../generated/job_pb';

export class GroupNode extends BaseNode {
  constructor(initialExpr = '') {
    super("Group");

    // Input socket
    this.addInput("in", new ClassicPreset.Input(dataStreamSocket, "Input"));

    // Group by expression
    this.addControl(
      "expr",
      new ClassicPreset.InputControl("text", {
        initial: initialExpr
      })
    );

    // Output socket
    this.addOutput("out", new ClassicPreset.Output(dataStreamSocket, "Grouped"));
  }

  toProto(nodeId: string): PipelineNode {
    const exprControl = this.controls["expr"] as ClassicPreset.InputControl<"text">;

    return new PipelineNode({
      id: nodeId,
      nodeType: {
        case: 'group',
        value: {
          keyMapperExpr: exprControl.value || ''
        }
      }
    });
  }
}
