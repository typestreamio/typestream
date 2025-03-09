// Code generated by protoc-gen-ts_proto. DO NOT EDIT.
// versions:
//   protoc-gen-ts_proto  v2.6.1
//   protoc               v5.29.3
// source: interactive_session.proto

/* eslint-disable */
import { BinaryReader, BinaryWriter } from "@bufbuild/protobuf/wire";

export const protobufPackage = "io.typestream.grpc";

export interface StartSessionRequest {
  userId: string;
}

export interface StartSessionResponse {
  sessionId: string;
}

export interface RunProgramRequest {
  sessionId: string;
  source: string;
}

export interface RunProgramResponse {
  id: string;
  env: { [key: string]: string };
  stdOut: string;
  stdErr: string;
  hasMoreOutput: boolean;
}

export interface RunProgramResponse_EnvEntry {
  key: string;
  value: string;
}

export interface GetProgramOutputRequest {
  sessionId: string;
  id: string;
}

export interface GetProgramOutputResponse {
  stdOut: string;
  stdErr: string;
}

export interface CompleteProgramRequest {
  sessionId: string;
  source: string;
  cursor: number;
}

export interface CompleteProgramResponse {
  value: string[];
}

export interface StopSessionRequest {
  sessionId: string;
}

export interface StopSessionResponse {
  stdOut: string;
  stdErr: string;
}

function createBaseStartSessionRequest(): StartSessionRequest {
  return { userId: "" };
}

export const StartSessionRequest: MessageFns<StartSessionRequest> = {
  encode(message: StartSessionRequest, writer: BinaryWriter = new BinaryWriter()): BinaryWriter {
    if (message.userId !== "") {
      writer.uint32(10).string(message.userId);
    }
    return writer;
  },

  decode(input: BinaryReader | Uint8Array, length?: number): StartSessionRequest {
    const reader = input instanceof BinaryReader ? input : new BinaryReader(input);
    let end = length === undefined ? reader.len : reader.pos + length;
    const message = createBaseStartSessionRequest();
    while (reader.pos < end) {
      const tag = reader.uint32();
      switch (tag >>> 3) {
        case 1: {
          if (tag !== 10) {
            break;
          }

          message.userId = reader.string();
          continue;
        }
      }
      if ((tag & 7) === 4 || tag === 0) {
        break;
      }
      reader.skip(tag & 7);
    }
    return message;
  },

  fromJSON(object: any): StartSessionRequest {
    return { userId: isSet(object.userId) ? globalThis.String(object.userId) : "" };
  },

  toJSON(message: StartSessionRequest): unknown {
    const obj: any = {};
    if (message.userId !== "") {
      obj.userId = message.userId;
    }
    return obj;
  },

  create<I extends Exact<DeepPartial<StartSessionRequest>, I>>(base?: I): StartSessionRequest {
    return StartSessionRequest.fromPartial(base ?? ({} as any));
  },
  fromPartial<I extends Exact<DeepPartial<StartSessionRequest>, I>>(object: I): StartSessionRequest {
    const message = createBaseStartSessionRequest();
    message.userId = object.userId ?? "";
    return message;
  },
};

function createBaseStartSessionResponse(): StartSessionResponse {
  return { sessionId: "" };
}

export const StartSessionResponse: MessageFns<StartSessionResponse> = {
  encode(message: StartSessionResponse, writer: BinaryWriter = new BinaryWriter()): BinaryWriter {
    if (message.sessionId !== "") {
      writer.uint32(10).string(message.sessionId);
    }
    return writer;
  },

  decode(input: BinaryReader | Uint8Array, length?: number): StartSessionResponse {
    const reader = input instanceof BinaryReader ? input : new BinaryReader(input);
    let end = length === undefined ? reader.len : reader.pos + length;
    const message = createBaseStartSessionResponse();
    while (reader.pos < end) {
      const tag = reader.uint32();
      switch (tag >>> 3) {
        case 1: {
          if (tag !== 10) {
            break;
          }

          message.sessionId = reader.string();
          continue;
        }
      }
      if ((tag & 7) === 4 || tag === 0) {
        break;
      }
      reader.skip(tag & 7);
    }
    return message;
  },

  fromJSON(object: any): StartSessionResponse {
    return { sessionId: isSet(object.sessionId) ? globalThis.String(object.sessionId) : "" };
  },

  toJSON(message: StartSessionResponse): unknown {
    const obj: any = {};
    if (message.sessionId !== "") {
      obj.sessionId = message.sessionId;
    }
    return obj;
  },

  create<I extends Exact<DeepPartial<StartSessionResponse>, I>>(base?: I): StartSessionResponse {
    return StartSessionResponse.fromPartial(base ?? ({} as any));
  },
  fromPartial<I extends Exact<DeepPartial<StartSessionResponse>, I>>(object: I): StartSessionResponse {
    const message = createBaseStartSessionResponse();
    message.sessionId = object.sessionId ?? "";
    return message;
  },
};

function createBaseRunProgramRequest(): RunProgramRequest {
  return { sessionId: "", source: "" };
}

export const RunProgramRequest: MessageFns<RunProgramRequest> = {
  encode(message: RunProgramRequest, writer: BinaryWriter = new BinaryWriter()): BinaryWriter {
    if (message.sessionId !== "") {
      writer.uint32(10).string(message.sessionId);
    }
    if (message.source !== "") {
      writer.uint32(18).string(message.source);
    }
    return writer;
  },

  decode(input: BinaryReader | Uint8Array, length?: number): RunProgramRequest {
    const reader = input instanceof BinaryReader ? input : new BinaryReader(input);
    let end = length === undefined ? reader.len : reader.pos + length;
    const message = createBaseRunProgramRequest();
    while (reader.pos < end) {
      const tag = reader.uint32();
      switch (tag >>> 3) {
        case 1: {
          if (tag !== 10) {
            break;
          }

          message.sessionId = reader.string();
          continue;
        }
        case 2: {
          if (tag !== 18) {
            break;
          }

          message.source = reader.string();
          continue;
        }
      }
      if ((tag & 7) === 4 || tag === 0) {
        break;
      }
      reader.skip(tag & 7);
    }
    return message;
  },

  fromJSON(object: any): RunProgramRequest {
    return {
      sessionId: isSet(object.sessionId) ? globalThis.String(object.sessionId) : "",
      source: isSet(object.source) ? globalThis.String(object.source) : "",
    };
  },

  toJSON(message: RunProgramRequest): unknown {
    const obj: any = {};
    if (message.sessionId !== "") {
      obj.sessionId = message.sessionId;
    }
    if (message.source !== "") {
      obj.source = message.source;
    }
    return obj;
  },

  create<I extends Exact<DeepPartial<RunProgramRequest>, I>>(base?: I): RunProgramRequest {
    return RunProgramRequest.fromPartial(base ?? ({} as any));
  },
  fromPartial<I extends Exact<DeepPartial<RunProgramRequest>, I>>(object: I): RunProgramRequest {
    const message = createBaseRunProgramRequest();
    message.sessionId = object.sessionId ?? "";
    message.source = object.source ?? "";
    return message;
  },
};

function createBaseRunProgramResponse(): RunProgramResponse {
  return { id: "", env: {}, stdOut: "", stdErr: "", hasMoreOutput: false };
}

export const RunProgramResponse: MessageFns<RunProgramResponse> = {
  encode(message: RunProgramResponse, writer: BinaryWriter = new BinaryWriter()): BinaryWriter {
    if (message.id !== "") {
      writer.uint32(10).string(message.id);
    }
    Object.entries(message.env).forEach(([key, value]) => {
      RunProgramResponse_EnvEntry.encode({ key: key as any, value }, writer.uint32(18).fork()).join();
    });
    if (message.stdOut !== "") {
      writer.uint32(26).string(message.stdOut);
    }
    if (message.stdErr !== "") {
      writer.uint32(34).string(message.stdErr);
    }
    if (message.hasMoreOutput !== false) {
      writer.uint32(40).bool(message.hasMoreOutput);
    }
    return writer;
  },

  decode(input: BinaryReader | Uint8Array, length?: number): RunProgramResponse {
    const reader = input instanceof BinaryReader ? input : new BinaryReader(input);
    let end = length === undefined ? reader.len : reader.pos + length;
    const message = createBaseRunProgramResponse();
    while (reader.pos < end) {
      const tag = reader.uint32();
      switch (tag >>> 3) {
        case 1: {
          if (tag !== 10) {
            break;
          }

          message.id = reader.string();
          continue;
        }
        case 2: {
          if (tag !== 18) {
            break;
          }

          const entry2 = RunProgramResponse_EnvEntry.decode(reader, reader.uint32());
          if (entry2.value !== undefined) {
            message.env[entry2.key] = entry2.value;
          }
          continue;
        }
        case 3: {
          if (tag !== 26) {
            break;
          }

          message.stdOut = reader.string();
          continue;
        }
        case 4: {
          if (tag !== 34) {
            break;
          }

          message.stdErr = reader.string();
          continue;
        }
        case 5: {
          if (tag !== 40) {
            break;
          }

          message.hasMoreOutput = reader.bool();
          continue;
        }
      }
      if ((tag & 7) === 4 || tag === 0) {
        break;
      }
      reader.skip(tag & 7);
    }
    return message;
  },

  fromJSON(object: any): RunProgramResponse {
    return {
      id: isSet(object.id) ? globalThis.String(object.id) : "",
      env: isObject(object.env)
        ? Object.entries(object.env).reduce<{ [key: string]: string }>((acc, [key, value]) => {
          acc[key] = String(value);
          return acc;
        }, {})
        : {},
      stdOut: isSet(object.stdOut) ? globalThis.String(object.stdOut) : "",
      stdErr: isSet(object.stdErr) ? globalThis.String(object.stdErr) : "",
      hasMoreOutput: isSet(object.hasMoreOutput) ? globalThis.Boolean(object.hasMoreOutput) : false,
    };
  },

  toJSON(message: RunProgramResponse): unknown {
    const obj: any = {};
    if (message.id !== "") {
      obj.id = message.id;
    }
    if (message.env) {
      const entries = Object.entries(message.env);
      if (entries.length > 0) {
        obj.env = {};
        entries.forEach(([k, v]) => {
          obj.env[k] = v;
        });
      }
    }
    if (message.stdOut !== "") {
      obj.stdOut = message.stdOut;
    }
    if (message.stdErr !== "") {
      obj.stdErr = message.stdErr;
    }
    if (message.hasMoreOutput !== false) {
      obj.hasMoreOutput = message.hasMoreOutput;
    }
    return obj;
  },

  create<I extends Exact<DeepPartial<RunProgramResponse>, I>>(base?: I): RunProgramResponse {
    return RunProgramResponse.fromPartial(base ?? ({} as any));
  },
  fromPartial<I extends Exact<DeepPartial<RunProgramResponse>, I>>(object: I): RunProgramResponse {
    const message = createBaseRunProgramResponse();
    message.id = object.id ?? "";
    message.env = Object.entries(object.env ?? {}).reduce<{ [key: string]: string }>((acc, [key, value]) => {
      if (value !== undefined) {
        acc[key] = globalThis.String(value);
      }
      return acc;
    }, {});
    message.stdOut = object.stdOut ?? "";
    message.stdErr = object.stdErr ?? "";
    message.hasMoreOutput = object.hasMoreOutput ?? false;
    return message;
  },
};

function createBaseRunProgramResponse_EnvEntry(): RunProgramResponse_EnvEntry {
  return { key: "", value: "" };
}

export const RunProgramResponse_EnvEntry: MessageFns<RunProgramResponse_EnvEntry> = {
  encode(message: RunProgramResponse_EnvEntry, writer: BinaryWriter = new BinaryWriter()): BinaryWriter {
    if (message.key !== "") {
      writer.uint32(10).string(message.key);
    }
    if (message.value !== "") {
      writer.uint32(18).string(message.value);
    }
    return writer;
  },

  decode(input: BinaryReader | Uint8Array, length?: number): RunProgramResponse_EnvEntry {
    const reader = input instanceof BinaryReader ? input : new BinaryReader(input);
    let end = length === undefined ? reader.len : reader.pos + length;
    const message = createBaseRunProgramResponse_EnvEntry();
    while (reader.pos < end) {
      const tag = reader.uint32();
      switch (tag >>> 3) {
        case 1: {
          if (tag !== 10) {
            break;
          }

          message.key = reader.string();
          continue;
        }
        case 2: {
          if (tag !== 18) {
            break;
          }

          message.value = reader.string();
          continue;
        }
      }
      if ((tag & 7) === 4 || tag === 0) {
        break;
      }
      reader.skip(tag & 7);
    }
    return message;
  },

  fromJSON(object: any): RunProgramResponse_EnvEntry {
    return {
      key: isSet(object.key) ? globalThis.String(object.key) : "",
      value: isSet(object.value) ? globalThis.String(object.value) : "",
    };
  },

  toJSON(message: RunProgramResponse_EnvEntry): unknown {
    const obj: any = {};
    if (message.key !== "") {
      obj.key = message.key;
    }
    if (message.value !== "") {
      obj.value = message.value;
    }
    return obj;
  },

  create<I extends Exact<DeepPartial<RunProgramResponse_EnvEntry>, I>>(base?: I): RunProgramResponse_EnvEntry {
    return RunProgramResponse_EnvEntry.fromPartial(base ?? ({} as any));
  },
  fromPartial<I extends Exact<DeepPartial<RunProgramResponse_EnvEntry>, I>>(object: I): RunProgramResponse_EnvEntry {
    const message = createBaseRunProgramResponse_EnvEntry();
    message.key = object.key ?? "";
    message.value = object.value ?? "";
    return message;
  },
};

function createBaseGetProgramOutputRequest(): GetProgramOutputRequest {
  return { sessionId: "", id: "" };
}

export const GetProgramOutputRequest: MessageFns<GetProgramOutputRequest> = {
  encode(message: GetProgramOutputRequest, writer: BinaryWriter = new BinaryWriter()): BinaryWriter {
    if (message.sessionId !== "") {
      writer.uint32(10).string(message.sessionId);
    }
    if (message.id !== "") {
      writer.uint32(18).string(message.id);
    }
    return writer;
  },

  decode(input: BinaryReader | Uint8Array, length?: number): GetProgramOutputRequest {
    const reader = input instanceof BinaryReader ? input : new BinaryReader(input);
    let end = length === undefined ? reader.len : reader.pos + length;
    const message = createBaseGetProgramOutputRequest();
    while (reader.pos < end) {
      const tag = reader.uint32();
      switch (tag >>> 3) {
        case 1: {
          if (tag !== 10) {
            break;
          }

          message.sessionId = reader.string();
          continue;
        }
        case 2: {
          if (tag !== 18) {
            break;
          }

          message.id = reader.string();
          continue;
        }
      }
      if ((tag & 7) === 4 || tag === 0) {
        break;
      }
      reader.skip(tag & 7);
    }
    return message;
  },

  fromJSON(object: any): GetProgramOutputRequest {
    return {
      sessionId: isSet(object.sessionId) ? globalThis.String(object.sessionId) : "",
      id: isSet(object.id) ? globalThis.String(object.id) : "",
    };
  },

  toJSON(message: GetProgramOutputRequest): unknown {
    const obj: any = {};
    if (message.sessionId !== "") {
      obj.sessionId = message.sessionId;
    }
    if (message.id !== "") {
      obj.id = message.id;
    }
    return obj;
  },

  create<I extends Exact<DeepPartial<GetProgramOutputRequest>, I>>(base?: I): GetProgramOutputRequest {
    return GetProgramOutputRequest.fromPartial(base ?? ({} as any));
  },
  fromPartial<I extends Exact<DeepPartial<GetProgramOutputRequest>, I>>(object: I): GetProgramOutputRequest {
    const message = createBaseGetProgramOutputRequest();
    message.sessionId = object.sessionId ?? "";
    message.id = object.id ?? "";
    return message;
  },
};

function createBaseGetProgramOutputResponse(): GetProgramOutputResponse {
  return { stdOut: "", stdErr: "" };
}

export const GetProgramOutputResponse: MessageFns<GetProgramOutputResponse> = {
  encode(message: GetProgramOutputResponse, writer: BinaryWriter = new BinaryWriter()): BinaryWriter {
    if (message.stdOut !== "") {
      writer.uint32(10).string(message.stdOut);
    }
    if (message.stdErr !== "") {
      writer.uint32(18).string(message.stdErr);
    }
    return writer;
  },

  decode(input: BinaryReader | Uint8Array, length?: number): GetProgramOutputResponse {
    const reader = input instanceof BinaryReader ? input : new BinaryReader(input);
    let end = length === undefined ? reader.len : reader.pos + length;
    const message = createBaseGetProgramOutputResponse();
    while (reader.pos < end) {
      const tag = reader.uint32();
      switch (tag >>> 3) {
        case 1: {
          if (tag !== 10) {
            break;
          }

          message.stdOut = reader.string();
          continue;
        }
        case 2: {
          if (tag !== 18) {
            break;
          }

          message.stdErr = reader.string();
          continue;
        }
      }
      if ((tag & 7) === 4 || tag === 0) {
        break;
      }
      reader.skip(tag & 7);
    }
    return message;
  },

  fromJSON(object: any): GetProgramOutputResponse {
    return {
      stdOut: isSet(object.stdOut) ? globalThis.String(object.stdOut) : "",
      stdErr: isSet(object.stdErr) ? globalThis.String(object.stdErr) : "",
    };
  },

  toJSON(message: GetProgramOutputResponse): unknown {
    const obj: any = {};
    if (message.stdOut !== "") {
      obj.stdOut = message.stdOut;
    }
    if (message.stdErr !== "") {
      obj.stdErr = message.stdErr;
    }
    return obj;
  },

  create<I extends Exact<DeepPartial<GetProgramOutputResponse>, I>>(base?: I): GetProgramOutputResponse {
    return GetProgramOutputResponse.fromPartial(base ?? ({} as any));
  },
  fromPartial<I extends Exact<DeepPartial<GetProgramOutputResponse>, I>>(object: I): GetProgramOutputResponse {
    const message = createBaseGetProgramOutputResponse();
    message.stdOut = object.stdOut ?? "";
    message.stdErr = object.stdErr ?? "";
    return message;
  },
};

function createBaseCompleteProgramRequest(): CompleteProgramRequest {
  return { sessionId: "", source: "", cursor: 0 };
}

export const CompleteProgramRequest: MessageFns<CompleteProgramRequest> = {
  encode(message: CompleteProgramRequest, writer: BinaryWriter = new BinaryWriter()): BinaryWriter {
    if (message.sessionId !== "") {
      writer.uint32(10).string(message.sessionId);
    }
    if (message.source !== "") {
      writer.uint32(18).string(message.source);
    }
    if (message.cursor !== 0) {
      writer.uint32(24).int32(message.cursor);
    }
    return writer;
  },

  decode(input: BinaryReader | Uint8Array, length?: number): CompleteProgramRequest {
    const reader = input instanceof BinaryReader ? input : new BinaryReader(input);
    let end = length === undefined ? reader.len : reader.pos + length;
    const message = createBaseCompleteProgramRequest();
    while (reader.pos < end) {
      const tag = reader.uint32();
      switch (tag >>> 3) {
        case 1: {
          if (tag !== 10) {
            break;
          }

          message.sessionId = reader.string();
          continue;
        }
        case 2: {
          if (tag !== 18) {
            break;
          }

          message.source = reader.string();
          continue;
        }
        case 3: {
          if (tag !== 24) {
            break;
          }

          message.cursor = reader.int32();
          continue;
        }
      }
      if ((tag & 7) === 4 || tag === 0) {
        break;
      }
      reader.skip(tag & 7);
    }
    return message;
  },

  fromJSON(object: any): CompleteProgramRequest {
    return {
      sessionId: isSet(object.sessionId) ? globalThis.String(object.sessionId) : "",
      source: isSet(object.source) ? globalThis.String(object.source) : "",
      cursor: isSet(object.cursor) ? globalThis.Number(object.cursor) : 0,
    };
  },

  toJSON(message: CompleteProgramRequest): unknown {
    const obj: any = {};
    if (message.sessionId !== "") {
      obj.sessionId = message.sessionId;
    }
    if (message.source !== "") {
      obj.source = message.source;
    }
    if (message.cursor !== 0) {
      obj.cursor = Math.round(message.cursor);
    }
    return obj;
  },

  create<I extends Exact<DeepPartial<CompleteProgramRequest>, I>>(base?: I): CompleteProgramRequest {
    return CompleteProgramRequest.fromPartial(base ?? ({} as any));
  },
  fromPartial<I extends Exact<DeepPartial<CompleteProgramRequest>, I>>(object: I): CompleteProgramRequest {
    const message = createBaseCompleteProgramRequest();
    message.sessionId = object.sessionId ?? "";
    message.source = object.source ?? "";
    message.cursor = object.cursor ?? 0;
    return message;
  },
};

function createBaseCompleteProgramResponse(): CompleteProgramResponse {
  return { value: [] };
}

export const CompleteProgramResponse: MessageFns<CompleteProgramResponse> = {
  encode(message: CompleteProgramResponse, writer: BinaryWriter = new BinaryWriter()): BinaryWriter {
    for (const v of message.value) {
      writer.uint32(10).string(v!);
    }
    return writer;
  },

  decode(input: BinaryReader | Uint8Array, length?: number): CompleteProgramResponse {
    const reader = input instanceof BinaryReader ? input : new BinaryReader(input);
    let end = length === undefined ? reader.len : reader.pos + length;
    const message = createBaseCompleteProgramResponse();
    while (reader.pos < end) {
      const tag = reader.uint32();
      switch (tag >>> 3) {
        case 1: {
          if (tag !== 10) {
            break;
          }

          message.value.push(reader.string());
          continue;
        }
      }
      if ((tag & 7) === 4 || tag === 0) {
        break;
      }
      reader.skip(tag & 7);
    }
    return message;
  },

  fromJSON(object: any): CompleteProgramResponse {
    return { value: globalThis.Array.isArray(object?.value) ? object.value.map((e: any) => globalThis.String(e)) : [] };
  },

  toJSON(message: CompleteProgramResponse): unknown {
    const obj: any = {};
    if (message.value?.length) {
      obj.value = message.value;
    }
    return obj;
  },

  create<I extends Exact<DeepPartial<CompleteProgramResponse>, I>>(base?: I): CompleteProgramResponse {
    return CompleteProgramResponse.fromPartial(base ?? ({} as any));
  },
  fromPartial<I extends Exact<DeepPartial<CompleteProgramResponse>, I>>(object: I): CompleteProgramResponse {
    const message = createBaseCompleteProgramResponse();
    message.value = object.value?.map((e) => e) || [];
    return message;
  },
};

function createBaseStopSessionRequest(): StopSessionRequest {
  return { sessionId: "" };
}

export const StopSessionRequest: MessageFns<StopSessionRequest> = {
  encode(message: StopSessionRequest, writer: BinaryWriter = new BinaryWriter()): BinaryWriter {
    if (message.sessionId !== "") {
      writer.uint32(10).string(message.sessionId);
    }
    return writer;
  },

  decode(input: BinaryReader | Uint8Array, length?: number): StopSessionRequest {
    const reader = input instanceof BinaryReader ? input : new BinaryReader(input);
    let end = length === undefined ? reader.len : reader.pos + length;
    const message = createBaseStopSessionRequest();
    while (reader.pos < end) {
      const tag = reader.uint32();
      switch (tag >>> 3) {
        case 1: {
          if (tag !== 10) {
            break;
          }

          message.sessionId = reader.string();
          continue;
        }
      }
      if ((tag & 7) === 4 || tag === 0) {
        break;
      }
      reader.skip(tag & 7);
    }
    return message;
  },

  fromJSON(object: any): StopSessionRequest {
    return { sessionId: isSet(object.sessionId) ? globalThis.String(object.sessionId) : "" };
  },

  toJSON(message: StopSessionRequest): unknown {
    const obj: any = {};
    if (message.sessionId !== "") {
      obj.sessionId = message.sessionId;
    }
    return obj;
  },

  create<I extends Exact<DeepPartial<StopSessionRequest>, I>>(base?: I): StopSessionRequest {
    return StopSessionRequest.fromPartial(base ?? ({} as any));
  },
  fromPartial<I extends Exact<DeepPartial<StopSessionRequest>, I>>(object: I): StopSessionRequest {
    const message = createBaseStopSessionRequest();
    message.sessionId = object.sessionId ?? "";
    return message;
  },
};

function createBaseStopSessionResponse(): StopSessionResponse {
  return { stdOut: "", stdErr: "" };
}

export const StopSessionResponse: MessageFns<StopSessionResponse> = {
  encode(message: StopSessionResponse, writer: BinaryWriter = new BinaryWriter()): BinaryWriter {
    if (message.stdOut !== "") {
      writer.uint32(10).string(message.stdOut);
    }
    if (message.stdErr !== "") {
      writer.uint32(18).string(message.stdErr);
    }
    return writer;
  },

  decode(input: BinaryReader | Uint8Array, length?: number): StopSessionResponse {
    const reader = input instanceof BinaryReader ? input : new BinaryReader(input);
    let end = length === undefined ? reader.len : reader.pos + length;
    const message = createBaseStopSessionResponse();
    while (reader.pos < end) {
      const tag = reader.uint32();
      switch (tag >>> 3) {
        case 1: {
          if (tag !== 10) {
            break;
          }

          message.stdOut = reader.string();
          continue;
        }
        case 2: {
          if (tag !== 18) {
            break;
          }

          message.stdErr = reader.string();
          continue;
        }
      }
      if ((tag & 7) === 4 || tag === 0) {
        break;
      }
      reader.skip(tag & 7);
    }
    return message;
  },

  fromJSON(object: any): StopSessionResponse {
    return {
      stdOut: isSet(object.stdOut) ? globalThis.String(object.stdOut) : "",
      stdErr: isSet(object.stdErr) ? globalThis.String(object.stdErr) : "",
    };
  },

  toJSON(message: StopSessionResponse): unknown {
    const obj: any = {};
    if (message.stdOut !== "") {
      obj.stdOut = message.stdOut;
    }
    if (message.stdErr !== "") {
      obj.stdErr = message.stdErr;
    }
    return obj;
  },

  create<I extends Exact<DeepPartial<StopSessionResponse>, I>>(base?: I): StopSessionResponse {
    return StopSessionResponse.fromPartial(base ?? ({} as any));
  },
  fromPartial<I extends Exact<DeepPartial<StopSessionResponse>, I>>(object: I): StopSessionResponse {
    const message = createBaseStopSessionResponse();
    message.stdOut = object.stdOut ?? "";
    message.stdErr = object.stdErr ?? "";
    return message;
  },
};

type Builtin = Date | Function | Uint8Array | string | number | boolean | undefined;

export type DeepPartial<T> = T extends Builtin ? T
  : T extends globalThis.Array<infer U> ? globalThis.Array<DeepPartial<U>>
  : T extends ReadonlyArray<infer U> ? ReadonlyArray<DeepPartial<U>>
  : T extends {} ? { [K in keyof T]?: DeepPartial<T[K]> }
  : Partial<T>;

type KeysOfUnion<T> = T extends T ? keyof T : never;
export type Exact<P, I extends P> = P extends Builtin ? P
  : P & { [K in keyof P]: Exact<P[K], I[K]> } & { [K in Exclude<keyof I, KeysOfUnion<P>>]: never };

function isObject(value: any): boolean {
  return typeof value === "object" && value !== null;
}

function isSet(value: any): boolean {
  return value !== null && value !== undefined;
}

export interface MessageFns<T> {
  encode(message: T, writer?: BinaryWriter): BinaryWriter;
  decode(input: BinaryReader | Uint8Array, length?: number): T;
  fromJSON(object: any): T;
  toJSON(message: T): unknown;
  create<I extends Exact<DeepPartial<T>, I>>(base?: I): T;
  fromPartial<I extends Exact<DeepPartial<T>, I>>(object: I): T;
}
