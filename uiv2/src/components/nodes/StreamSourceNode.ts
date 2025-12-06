import { ClassicPreset } from 'rete';
import { BaseNode } from './BaseNode';
import { dataStreamSocket } from '../../types/rete';
import { PipelineNode, DataStreamProto, Encoding } from '../../generated/job_pb';

export class StreamSourceNode extends BaseNode {
  constructor(initialPath = '/dev/kafka/local/topics/', initialEncoding = Encoding.STRING) {
    super("Stream Source");

    // Topic path input
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

    // Output socket
    this.addOutput("out", new ClassicPreset.Output(dataStreamSocket, "Stream"));
  }

  toProto(nodeId: string): PipelineNode {
    const pathControl = this.controls["path"] as ClassicPreset.InputControl<"text">;
    const encodingControl = this.controls["encoding"] as ClassicPreset.InputControl<"text">;

    return new PipelineNode({
      id: nodeId,
      nodeType: {
        case: 'streamSource',
        value: {
          dataStream: new DataStreamProto({ path: pathControl.value || '' }),
          encoding: parseInt(encodingControl.value || String(Encoding.STRING)) as Encoding
        }
      }
    });
  }
}
