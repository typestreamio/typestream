import { ClassicPreset, type GetSchemes } from "rete";
import type { PipelineNode } from "../generated/job_pb";

// Socket type for data stream connections
export const dataStreamSocket = new ClassicPreset.Socket("DataStream");

// Base node interface with proto conversion
export interface TypeStreamNode extends ClassicPreset.Node {
  toProto(nodeId: string): PipelineNode;
}

// Rete editor schemes
export type Schemes = GetSchemes<
  ClassicPreset.Node,
  ClassicPreset.Connection<ClassicPreset.Node, ClassicPreset.Node>
>;

export type AreaExtra = any;
