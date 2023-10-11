// Code generated by protoc-gen-go-grpc. DO NOT EDIT.
// versions:
// - protoc-gen-go-grpc v1.2.0
// - protoc             v4.24.4
// source: interactive_session.proto

package interactive_session_service

import (
	context "context"
	grpc "google.golang.org/grpc"
	codes "google.golang.org/grpc/codes"
	status "google.golang.org/grpc/status"
)

// This is a compile-time assertion to ensure that this generated file
// is compatible with the grpc package it is being compiled against.
// Requires gRPC-Go v1.32.0 or later.
const _ = grpc.SupportPackageIsVersion7

// InteractiveSessionServiceClient is the client API for InteractiveSessionService service.
//
// For semantics around ctx use and closing/ending streaming RPCs, please refer to https://pkg.go.dev/google.golang.org/grpc/?tab=doc#ClientConn.NewStream.
type InteractiveSessionServiceClient interface {
	StartSession(ctx context.Context, in *StartSessionRequest, opts ...grpc.CallOption) (*StartSessionResponse, error)
	RunProgram(ctx context.Context, in *RunProgramRequest, opts ...grpc.CallOption) (*RunProgramResponse, error)
	GetProgramOutput(ctx context.Context, in *GetProgramOutputRequest, opts ...grpc.CallOption) (InteractiveSessionService_GetProgramOutputClient, error)
	CompleteProgram(ctx context.Context, in *CompleteProgramRequest, opts ...grpc.CallOption) (*CompleteProgramResponse, error)
	StopSession(ctx context.Context, in *StopSessionRequest, opts ...grpc.CallOption) (*StopSessionResponse, error)
}

type interactiveSessionServiceClient struct {
	cc grpc.ClientConnInterface
}

func NewInteractiveSessionServiceClient(cc grpc.ClientConnInterface) InteractiveSessionServiceClient {
	return &interactiveSessionServiceClient{cc}
}

func (c *interactiveSessionServiceClient) StartSession(ctx context.Context, in *StartSessionRequest, opts ...grpc.CallOption) (*StartSessionResponse, error) {
	out := new(StartSessionResponse)
	err := c.cc.Invoke(ctx, "/io.typestream.grpc.InteractiveSessionService/StartSession", in, out, opts...)
	if err != nil {
		return nil, err
	}
	return out, nil
}

func (c *interactiveSessionServiceClient) RunProgram(ctx context.Context, in *RunProgramRequest, opts ...grpc.CallOption) (*RunProgramResponse, error) {
	out := new(RunProgramResponse)
	err := c.cc.Invoke(ctx, "/io.typestream.grpc.InteractiveSessionService/RunProgram", in, out, opts...)
	if err != nil {
		return nil, err
	}
	return out, nil
}

func (c *interactiveSessionServiceClient) GetProgramOutput(ctx context.Context, in *GetProgramOutputRequest, opts ...grpc.CallOption) (InteractiveSessionService_GetProgramOutputClient, error) {
	stream, err := c.cc.NewStream(ctx, &InteractiveSessionService_ServiceDesc.Streams[0], "/io.typestream.grpc.InteractiveSessionService/GetProgramOutput", opts...)
	if err != nil {
		return nil, err
	}
	x := &interactiveSessionServiceGetProgramOutputClient{stream}
	if err := x.ClientStream.SendMsg(in); err != nil {
		return nil, err
	}
	if err := x.ClientStream.CloseSend(); err != nil {
		return nil, err
	}
	return x, nil
}

type InteractiveSessionService_GetProgramOutputClient interface {
	Recv() (*GetProgramOutputResponse, error)
	grpc.ClientStream
}

type interactiveSessionServiceGetProgramOutputClient struct {
	grpc.ClientStream
}

func (x *interactiveSessionServiceGetProgramOutputClient) Recv() (*GetProgramOutputResponse, error) {
	m := new(GetProgramOutputResponse)
	if err := x.ClientStream.RecvMsg(m); err != nil {
		return nil, err
	}
	return m, nil
}

func (c *interactiveSessionServiceClient) CompleteProgram(ctx context.Context, in *CompleteProgramRequest, opts ...grpc.CallOption) (*CompleteProgramResponse, error) {
	out := new(CompleteProgramResponse)
	err := c.cc.Invoke(ctx, "/io.typestream.grpc.InteractiveSessionService/CompleteProgram", in, out, opts...)
	if err != nil {
		return nil, err
	}
	return out, nil
}

func (c *interactiveSessionServiceClient) StopSession(ctx context.Context, in *StopSessionRequest, opts ...grpc.CallOption) (*StopSessionResponse, error) {
	out := new(StopSessionResponse)
	err := c.cc.Invoke(ctx, "/io.typestream.grpc.InteractiveSessionService/StopSession", in, out, opts...)
	if err != nil {
		return nil, err
	}
	return out, nil
}

// InteractiveSessionServiceServer is the server API for InteractiveSessionService service.
// All implementations must embed UnimplementedInteractiveSessionServiceServer
// for forward compatibility
type InteractiveSessionServiceServer interface {
	StartSession(context.Context, *StartSessionRequest) (*StartSessionResponse, error)
	RunProgram(context.Context, *RunProgramRequest) (*RunProgramResponse, error)
	GetProgramOutput(*GetProgramOutputRequest, InteractiveSessionService_GetProgramOutputServer) error
	CompleteProgram(context.Context, *CompleteProgramRequest) (*CompleteProgramResponse, error)
	StopSession(context.Context, *StopSessionRequest) (*StopSessionResponse, error)
	mustEmbedUnimplementedInteractiveSessionServiceServer()
}

// UnimplementedInteractiveSessionServiceServer must be embedded to have forward compatible implementations.
type UnimplementedInteractiveSessionServiceServer struct {
}

func (UnimplementedInteractiveSessionServiceServer) StartSession(context.Context, *StartSessionRequest) (*StartSessionResponse, error) {
	return nil, status.Errorf(codes.Unimplemented, "method StartSession not implemented")
}
func (UnimplementedInteractiveSessionServiceServer) RunProgram(context.Context, *RunProgramRequest) (*RunProgramResponse, error) {
	return nil, status.Errorf(codes.Unimplemented, "method RunProgram not implemented")
}
func (UnimplementedInteractiveSessionServiceServer) GetProgramOutput(*GetProgramOutputRequest, InteractiveSessionService_GetProgramOutputServer) error {
	return status.Errorf(codes.Unimplemented, "method GetProgramOutput not implemented")
}
func (UnimplementedInteractiveSessionServiceServer) CompleteProgram(context.Context, *CompleteProgramRequest) (*CompleteProgramResponse, error) {
	return nil, status.Errorf(codes.Unimplemented, "method CompleteProgram not implemented")
}
func (UnimplementedInteractiveSessionServiceServer) StopSession(context.Context, *StopSessionRequest) (*StopSessionResponse, error) {
	return nil, status.Errorf(codes.Unimplemented, "method StopSession not implemented")
}
func (UnimplementedInteractiveSessionServiceServer) mustEmbedUnimplementedInteractiveSessionServiceServer() {
}

// UnsafeInteractiveSessionServiceServer may be embedded to opt out of forward compatibility for this service.
// Use of this interface is not recommended, as added methods to InteractiveSessionServiceServer will
// result in compilation errors.
type UnsafeInteractiveSessionServiceServer interface {
	mustEmbedUnimplementedInteractiveSessionServiceServer()
}

func RegisterInteractiveSessionServiceServer(s grpc.ServiceRegistrar, srv InteractiveSessionServiceServer) {
	s.RegisterService(&InteractiveSessionService_ServiceDesc, srv)
}

func _InteractiveSessionService_StartSession_Handler(srv interface{}, ctx context.Context, dec func(interface{}) error, interceptor grpc.UnaryServerInterceptor) (interface{}, error) {
	in := new(StartSessionRequest)
	if err := dec(in); err != nil {
		return nil, err
	}
	if interceptor == nil {
		return srv.(InteractiveSessionServiceServer).StartSession(ctx, in)
	}
	info := &grpc.UnaryServerInfo{
		Server:     srv,
		FullMethod: "/io.typestream.grpc.InteractiveSessionService/StartSession",
	}
	handler := func(ctx context.Context, req interface{}) (interface{}, error) {
		return srv.(InteractiveSessionServiceServer).StartSession(ctx, req.(*StartSessionRequest))
	}
	return interceptor(ctx, in, info, handler)
}

func _InteractiveSessionService_RunProgram_Handler(srv interface{}, ctx context.Context, dec func(interface{}) error, interceptor grpc.UnaryServerInterceptor) (interface{}, error) {
	in := new(RunProgramRequest)
	if err := dec(in); err != nil {
		return nil, err
	}
	if interceptor == nil {
		return srv.(InteractiveSessionServiceServer).RunProgram(ctx, in)
	}
	info := &grpc.UnaryServerInfo{
		Server:     srv,
		FullMethod: "/io.typestream.grpc.InteractiveSessionService/RunProgram",
	}
	handler := func(ctx context.Context, req interface{}) (interface{}, error) {
		return srv.(InteractiveSessionServiceServer).RunProgram(ctx, req.(*RunProgramRequest))
	}
	return interceptor(ctx, in, info, handler)
}

func _InteractiveSessionService_GetProgramOutput_Handler(srv interface{}, stream grpc.ServerStream) error {
	m := new(GetProgramOutputRequest)
	if err := stream.RecvMsg(m); err != nil {
		return err
	}
	return srv.(InteractiveSessionServiceServer).GetProgramOutput(m, &interactiveSessionServiceGetProgramOutputServer{stream})
}

type InteractiveSessionService_GetProgramOutputServer interface {
	Send(*GetProgramOutputResponse) error
	grpc.ServerStream
}

type interactiveSessionServiceGetProgramOutputServer struct {
	grpc.ServerStream
}

func (x *interactiveSessionServiceGetProgramOutputServer) Send(m *GetProgramOutputResponse) error {
	return x.ServerStream.SendMsg(m)
}

func _InteractiveSessionService_CompleteProgram_Handler(srv interface{}, ctx context.Context, dec func(interface{}) error, interceptor grpc.UnaryServerInterceptor) (interface{}, error) {
	in := new(CompleteProgramRequest)
	if err := dec(in); err != nil {
		return nil, err
	}
	if interceptor == nil {
		return srv.(InteractiveSessionServiceServer).CompleteProgram(ctx, in)
	}
	info := &grpc.UnaryServerInfo{
		Server:     srv,
		FullMethod: "/io.typestream.grpc.InteractiveSessionService/CompleteProgram",
	}
	handler := func(ctx context.Context, req interface{}) (interface{}, error) {
		return srv.(InteractiveSessionServiceServer).CompleteProgram(ctx, req.(*CompleteProgramRequest))
	}
	return interceptor(ctx, in, info, handler)
}

func _InteractiveSessionService_StopSession_Handler(srv interface{}, ctx context.Context, dec func(interface{}) error, interceptor grpc.UnaryServerInterceptor) (interface{}, error) {
	in := new(StopSessionRequest)
	if err := dec(in); err != nil {
		return nil, err
	}
	if interceptor == nil {
		return srv.(InteractiveSessionServiceServer).StopSession(ctx, in)
	}
	info := &grpc.UnaryServerInfo{
		Server:     srv,
		FullMethod: "/io.typestream.grpc.InteractiveSessionService/StopSession",
	}
	handler := func(ctx context.Context, req interface{}) (interface{}, error) {
		return srv.(InteractiveSessionServiceServer).StopSession(ctx, req.(*StopSessionRequest))
	}
	return interceptor(ctx, in, info, handler)
}

// InteractiveSessionService_ServiceDesc is the grpc.ServiceDesc for InteractiveSessionService service.
// It's only intended for direct use with grpc.RegisterService,
// and not to be introspected or modified (even as a copy)
var InteractiveSessionService_ServiceDesc = grpc.ServiceDesc{
	ServiceName: "io.typestream.grpc.InteractiveSessionService",
	HandlerType: (*InteractiveSessionServiceServer)(nil),
	Methods: []grpc.MethodDesc{
		{
			MethodName: "StartSession",
			Handler:    _InteractiveSessionService_StartSession_Handler,
		},
		{
			MethodName: "RunProgram",
			Handler:    _InteractiveSessionService_RunProgram_Handler,
		},
		{
			MethodName: "CompleteProgram",
			Handler:    _InteractiveSessionService_CompleteProgram_Handler,
		},
		{
			MethodName: "StopSession",
			Handler:    _InteractiveSessionService_StopSession_Handler,
		},
	},
	Streams: []grpc.StreamDesc{
		{
			StreamName:    "GetProgramOutput",
			Handler:       _InteractiveSessionService_GetProgramOutput_Handler,
			ServerStreams: true,
		},
	},
	Metadata: "interactive_session.proto",
}
