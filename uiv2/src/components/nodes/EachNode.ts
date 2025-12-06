import { ClassicPreset } from 'rete';
import { BaseNode } from './BaseNode';
import { dataStreamSocket } from '../../types/rete';
import { PipelineNode } from '../../generated/job_pb';

export class EachNode extends BaseNode {
  constructor(initialExpr = '') {
    super("Each");

    // Input socket
    this.addInput("in", new ClassicPreset.Input(dataStreamSocket, "Input"));

    // Each expression
    this.addControl(
      "expr",
      new ClassicPreset.InputControl("text", {
        initial: initialExpr
      })
    );

    // Output socket
    this.addOutput("out", new ClassicPreset.Output(dataStreamSocket, "Output"));
  }

  toProto(nodeId: string): PipelineNode {
    const exprControl = this.controls["expr"] as ClassicPreset.InputControl<"text">;

    return new PipelineNode({
      id: nodeId,
      nodeType: {
        case: 'each',
        value: {
          fnExpr: exprControl.value || ''
        }
      }
    });
  }
}
