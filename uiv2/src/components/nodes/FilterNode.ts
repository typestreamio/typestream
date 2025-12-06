import { ClassicPreset } from 'rete';
import { BaseNode } from './BaseNode';
import { dataStreamSocket } from '../../types/rete';
import { PipelineNode, PredicateProto } from '../../generated/job_pb';

export class FilterNode extends BaseNode {
  constructor(initialPredicate = '', initialByKey = false) {
    super("Filter");

    // Input socket
    this.addInput("in", new ClassicPreset.Input(dataStreamSocket, "Input"));

    // Predicate expression
    this.addControl(
      "predicate",
      new ClassicPreset.InputControl("text", {
        initial: initialPredicate
      })
    );

    // By key checkbox
    this.addControl(
      "byKey",
      new ClassicPreset.InputControl("text", {
        initial: String(initialByKey)
      })
    );

    // Output socket
    this.addOutput("out", new ClassicPreset.Output(dataStreamSocket, "Filtered"));
  }

  toProto(nodeId: string): PipelineNode {
    const predicateControl = this.controls["predicate"] as ClassicPreset.InputControl<"text">;
    const byKeyControl = this.controls["byKey"] as ClassicPreset.InputControl<"text">;

    return new PipelineNode({
      id: nodeId,
      nodeType: {
        case: 'filter',
        value: {
          byKey: byKeyControl.value === 'true',
          predicate: new PredicateProto({ expr: predicateControl.value || '' })
        }
      }
    });
  }
}
