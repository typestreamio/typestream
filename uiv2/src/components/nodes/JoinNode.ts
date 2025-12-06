import { ClassicPreset } from 'rete';
import { BaseNode } from './BaseNode';
import { dataStreamSocket } from '../../types/rete';
import { PipelineNode, JoinTypeProto } from '../../generated/job_pb';

export class JoinNode extends BaseNode {
  constructor(initialByKey = false, initialIsLookup = false) {
    super("Join");

    // Two input sockets for joining
    this.addInput("left", new ClassicPreset.Input(dataStreamSocket, "Left"));
    this.addInput("right", new ClassicPreset.Input(dataStreamSocket, "Right"));

    // Join by key checkbox
    this.addControl(
      "byKey",
      new ClassicPreset.InputControl("text", {
        initial: String(initialByKey)
      })
    );

    // Is lookup checkbox
    this.addControl(
      "isLookup",
      new ClassicPreset.InputControl("text", {
        initial: String(initialIsLookup)
      })
    );

    // Output socket
    this.addOutput("out", new ClassicPreset.Output(dataStreamSocket, "Joined"));
  }

  toProto(nodeId: string): PipelineNode {
    const byKeyControl = this.controls["byKey"] as ClassicPreset.InputControl<"text">;
    const isLookupControl = this.controls["isLookup"] as ClassicPreset.InputControl<"text">;

    return new PipelineNode({
      id: nodeId,
      nodeType: {
        case: 'join',
        value: {
          joinType: new JoinTypeProto({
            byKey: byKeyControl.value === 'true',
            isLookup: isLookupControl.value === 'true'
          })
        }
      }
    });
  }
}
