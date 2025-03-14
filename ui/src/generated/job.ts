// Code generated by protoc-gen-ts_proto. DO NOT EDIT.
// versions:
//   protoc-gen-ts_proto  v2.6.1
//   protoc               v5.29.3
// source: job.proto

/* eslint-disable */
import { BinaryReader, BinaryWriter } from "@bufbuild/protobuf/wire";
import { grpc } from "@improbable-eng/grpc-web";
import { BrowserHeaders } from "browser-headers";

export const protobufPackage = "io.typestream.grpc";

export interface CreateJobRequest {
  userId: string;
  source: string;
}

export interface CreateJobResponse {
  success: boolean;
  jobId: string;
  error: string;
}

function createBaseCreateJobRequest(): CreateJobRequest {
  return { userId: "", source: "" };
}

export const CreateJobRequest: MessageFns<CreateJobRequest> = {
  encode(message: CreateJobRequest, writer: BinaryWriter = new BinaryWriter()): BinaryWriter {
    if (message.userId !== "") {
      writer.uint32(10).string(message.userId);
    }
    if (message.source !== "") {
      writer.uint32(18).string(message.source);
    }
    return writer;
  },

  decode(input: BinaryReader | Uint8Array, length?: number): CreateJobRequest {
    const reader = input instanceof BinaryReader ? input : new BinaryReader(input);
    let end = length === undefined ? reader.len : reader.pos + length;
    const message = createBaseCreateJobRequest();
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

  fromJSON(object: any): CreateJobRequest {
    return {
      userId: isSet(object.userId) ? globalThis.String(object.userId) : "",
      source: isSet(object.source) ? globalThis.String(object.source) : "",
    };
  },

  toJSON(message: CreateJobRequest): unknown {
    const obj: any = {};
    if (message.userId !== "") {
      obj.userId = message.userId;
    }
    if (message.source !== "") {
      obj.source = message.source;
    }
    return obj;
  },

  create<I extends Exact<DeepPartial<CreateJobRequest>, I>>(base?: I): CreateJobRequest {
    return CreateJobRequest.fromPartial(base ?? ({} as any));
  },
  fromPartial<I extends Exact<DeepPartial<CreateJobRequest>, I>>(object: I): CreateJobRequest {
    const message = createBaseCreateJobRequest();
    message.userId = object.userId ?? "";
    message.source = object.source ?? "";
    return message;
  },
};

function createBaseCreateJobResponse(): CreateJobResponse {
  return { success: false, jobId: "", error: "" };
}

export const CreateJobResponse: MessageFns<CreateJobResponse> = {
  encode(message: CreateJobResponse, writer: BinaryWriter = new BinaryWriter()): BinaryWriter {
    if (message.success !== false) {
      writer.uint32(8).bool(message.success);
    }
    if (message.jobId !== "") {
      writer.uint32(18).string(message.jobId);
    }
    if (message.error !== "") {
      writer.uint32(26).string(message.error);
    }
    return writer;
  },

  decode(input: BinaryReader | Uint8Array, length?: number): CreateJobResponse {
    const reader = input instanceof BinaryReader ? input : new BinaryReader(input);
    let end = length === undefined ? reader.len : reader.pos + length;
    const message = createBaseCreateJobResponse();
    while (reader.pos < end) {
      const tag = reader.uint32();
      switch (tag >>> 3) {
        case 1: {
          if (tag !== 8) {
            break;
          }

          message.success = reader.bool();
          continue;
        }
        case 2: {
          if (tag !== 18) {
            break;
          }

          message.jobId = reader.string();
          continue;
        }
        case 3: {
          if (tag !== 26) {
            break;
          }

          message.error = reader.string();
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

  fromJSON(object: any): CreateJobResponse {
    return {
      success: isSet(object.success) ? globalThis.Boolean(object.success) : false,
      jobId: isSet(object.jobId) ? globalThis.String(object.jobId) : "",
      error: isSet(object.error) ? globalThis.String(object.error) : "",
    };
  },

  toJSON(message: CreateJobResponse): unknown {
    const obj: any = {};
    if (message.success !== false) {
      obj.success = message.success;
    }
    if (message.jobId !== "") {
      obj.jobId = message.jobId;
    }
    if (message.error !== "") {
      obj.error = message.error;
    }
    return obj;
  },

  create<I extends Exact<DeepPartial<CreateJobResponse>, I>>(base?: I): CreateJobResponse {
    return CreateJobResponse.fromPartial(base ?? ({} as any));
  },
  fromPartial<I extends Exact<DeepPartial<CreateJobResponse>, I>>(object: I): CreateJobResponse {
    const message = createBaseCreateJobResponse();
    message.success = object.success ?? false;
    message.jobId = object.jobId ?? "";
    message.error = object.error ?? "";
    return message;
  },
};

export interface JobService {
  CreateJob(request: DeepPartial<CreateJobRequest>, metadata?: grpc.Metadata): Promise<CreateJobResponse>;
}

export class JobServiceClientImpl implements JobService {
  private readonly rpc: Rpc;

  constructor(rpc: Rpc) {
    this.rpc = rpc;
    this.CreateJob = this.CreateJob.bind(this);
  }

  CreateJob(request: DeepPartial<CreateJobRequest>, metadata?: grpc.Metadata): Promise<CreateJobResponse> {
    return this.rpc.unary(JobServiceCreateJobDesc, CreateJobRequest.fromPartial(request), metadata);
  }
}

export const JobServiceDesc = { serviceName: "io.typestream.grpc.JobService" };

export const JobServiceCreateJobDesc: UnaryMethodDefinitionish = {
  methodName: "CreateJob",
  service: JobServiceDesc,
  requestStream: false,
  responseStream: false,
  requestType: {
    serializeBinary() {
      return CreateJobRequest.encode(this).finish();
    },
  } as any,
  responseType: {
    deserializeBinary(data: Uint8Array) {
      const value = CreateJobResponse.decode(data);
      return {
        ...value,
        toObject() {
          return value;
        },
      };
    },
  } as any,
};

interface UnaryMethodDefinitionishR extends grpc.UnaryMethodDefinition<any, any> {
  requestStream: any;
  responseStream: any;
}

type UnaryMethodDefinitionish = UnaryMethodDefinitionishR;

interface Rpc {
  unary<T extends UnaryMethodDefinitionish>(
    methodDesc: T,
    request: any,
    metadata: grpc.Metadata | undefined,
  ): Promise<any>;
}

export class GrpcWebImpl {
  private host: string;
  private options: {
    transport?: grpc.TransportFactory;

    debug?: boolean;
    metadata?: grpc.Metadata;
    upStreamRetryCodes?: number[];
  };

  constructor(
    host: string,
    options: {
      transport?: grpc.TransportFactory;

      debug?: boolean;
      metadata?: grpc.Metadata;
      upStreamRetryCodes?: number[];
    },
  ) {
    this.host = host;
    this.options = options;
  }

  unary<T extends UnaryMethodDefinitionish>(
    methodDesc: T,
    _request: any,
    metadata: grpc.Metadata | undefined,
  ): Promise<any> {
    const request = { ..._request, ...methodDesc.requestType };
    const maybeCombinedMetadata = metadata && this.options.metadata
      ? new BrowserHeaders({ ...this.options?.metadata.headersMap, ...metadata?.headersMap })
      : metadata ?? this.options.metadata;
    return new Promise((resolve, reject) => {
      grpc.unary(methodDesc, {
        request,
        host: this.host,
        metadata: maybeCombinedMetadata ?? {},
        ...(this.options.transport !== undefined ? { transport: this.options.transport } : {}),
        debug: this.options.debug ?? false,
        onEnd: function (response) {
          if (response.status === grpc.Code.OK) {
            resolve(response.message!.toObject());
          } else {
            const err = new GrpcWebError(response.statusMessage, response.status, response.trailers);
            reject(err);
          }
        },
      });
    });
  }
}

type Builtin = Date | Function | Uint8Array | string | number | boolean | undefined;

export type DeepPartial<T> = T extends Builtin ? T
  : T extends globalThis.Array<infer U> ? globalThis.Array<DeepPartial<U>>
  : T extends ReadonlyArray<infer U> ? ReadonlyArray<DeepPartial<U>>
  : T extends {} ? { [K in keyof T]?: DeepPartial<T[K]> }
  : Partial<T>;

type KeysOfUnion<T> = T extends T ? keyof T : never;
export type Exact<P, I extends P> = P extends Builtin ? P
  : P & { [K in keyof P]: Exact<P[K], I[K]> } & { [K in Exclude<keyof I, KeysOfUnion<P>>]: never };

function isSet(value: any): boolean {
  return value !== null && value !== undefined;
}

export class GrpcWebError extends globalThis.Error {
  constructor(message: string, public code: grpc.Code, public metadata: grpc.Metadata) {
    super(message);
  }
}

export interface MessageFns<T> {
  encode(message: T, writer?: BinaryWriter): BinaryWriter;
  decode(input: BinaryReader | Uint8Array, length?: number): T;
  fromJSON(object: any): T;
  toJSON(message: T): unknown;
  create<I extends Exact<DeepPartial<T>, I>>(base?: I): T;
  fromPartial<I extends Exact<DeepPartial<T>, I>>(object: I): T;
}
