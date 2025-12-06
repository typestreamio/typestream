import { ClassicPreset } from "rete";
import type { PipelineNode } from "../generated/job_pb";

// Socket type for data stream connections
export const dataStreamSocket = new ClassicPreset.Socket("DataStream");

// Base node interface with proto conversion
export interface TypeStreamNode extends ClassicPreset.Node {
  toProto(nodeId: string): PipelineNode;
}

// Rete editor schemes - use ClassicPreset.Node as the constraint
export type Schemes = ClassicPreset.ClassicScheme;

export type AreaExtra = ClassicPreset.AreaExtra;
