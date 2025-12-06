import { ClassicPreset } from 'rete';
import { BaseNode } from './BaseNode';
import { dataStreamSocket } from '../../types/rete';
import { PipelineNode, DataStreamProto, Encoding } from '../../generated/job_pb';

export class SinkNode extends BaseNode {
  constructor(initialPath = '', initialEncoding = Encoding.STRING) {
    super("Sink");

    // Input socket
    this.addInput("in", new ClassicPreset.Input(dataStreamSocket, "Input"));

    // Output path
    this.addControl(
      "path",
      new ClassicPreset.InputControl("text", {
        initial: initialPath
      })
    );

    // Encoding selector
    this.addControl(
      "encoding",
      new ClassicPreset.InputControl("text", {
        initial: String(initialEncoding)
      })
    );
  }

  toProto(nodeId: string): PipelineNode {
    const pathControl = this.controls["path"] as ClassicPreset.InputControl<"text">;
    const encodingControl = this.controls["encoding"] as ClassicPreset.InputControl<"text">;

    return new PipelineNode({
      id: nodeId,
      nodeType: {
        case: 'sink',
        value: {
          output: new DataStreamProto({ path: pathControl.value || '' }),
          encoding: parseInt(encodingControl.value || String(Encoding.STRING)) as Encoding
        }
      }
    });
  }
}
