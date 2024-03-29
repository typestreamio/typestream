// Code generated by protoc-gen-go-grpc. DO NOT EDIT.
// versions:
// - protoc-gen-go-grpc v1.2.0
// - protoc             v4.25.2
// source: filesystem.proto

package filesystem_service

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

// FileSystemServiceClient is the client API for FileSystemService service.
//
// For semantics around ctx use and closing/ending streaming RPCs, please refer to https://pkg.go.dev/google.golang.org/grpc/?tab=doc#ClientConn.NewStream.
type FileSystemServiceClient interface {
	Mount(ctx context.Context, in *MountRequest, opts ...grpc.CallOption) (*MountResponse, error)
	Unmount(ctx context.Context, in *UnmountRequest, opts ...grpc.CallOption) (*UnmountResponse, error)
}

type fileSystemServiceClient struct {
	cc grpc.ClientConnInterface
}

func NewFileSystemServiceClient(cc grpc.ClientConnInterface) FileSystemServiceClient {
	return &fileSystemServiceClient{cc}
}

func (c *fileSystemServiceClient) Mount(ctx context.Context, in *MountRequest, opts ...grpc.CallOption) (*MountResponse, error) {
	out := new(MountResponse)
	err := c.cc.Invoke(ctx, "/io.typestream.grpc.FileSystemService/Mount", in, out, opts...)
	if err != nil {
		return nil, err
	}
	return out, nil
}

func (c *fileSystemServiceClient) Unmount(ctx context.Context, in *UnmountRequest, opts ...grpc.CallOption) (*UnmountResponse, error) {
	out := new(UnmountResponse)
	err := c.cc.Invoke(ctx, "/io.typestream.grpc.FileSystemService/Unmount", in, out, opts...)
	if err != nil {
		return nil, err
	}
	return out, nil
}

// FileSystemServiceServer is the server API for FileSystemService service.
// All implementations must embed UnimplementedFileSystemServiceServer
// for forward compatibility
type FileSystemServiceServer interface {
	Mount(context.Context, *MountRequest) (*MountResponse, error)
	Unmount(context.Context, *UnmountRequest) (*UnmountResponse, error)
	mustEmbedUnimplementedFileSystemServiceServer()
}

// UnimplementedFileSystemServiceServer must be embedded to have forward compatible implementations.
type UnimplementedFileSystemServiceServer struct {
}

func (UnimplementedFileSystemServiceServer) Mount(context.Context, *MountRequest) (*MountResponse, error) {
	return nil, status.Errorf(codes.Unimplemented, "method Mount not implemented")
}
func (UnimplementedFileSystemServiceServer) Unmount(context.Context, *UnmountRequest) (*UnmountResponse, error) {
	return nil, status.Errorf(codes.Unimplemented, "method Unmount not implemented")
}
func (UnimplementedFileSystemServiceServer) mustEmbedUnimplementedFileSystemServiceServer() {}

// UnsafeFileSystemServiceServer may be embedded to opt out of forward compatibility for this service.
// Use of this interface is not recommended, as added methods to FileSystemServiceServer will
// result in compilation errors.
type UnsafeFileSystemServiceServer interface {
	mustEmbedUnimplementedFileSystemServiceServer()
}

func RegisterFileSystemServiceServer(s grpc.ServiceRegistrar, srv FileSystemServiceServer) {
	s.RegisterService(&FileSystemService_ServiceDesc, srv)
}

func _FileSystemService_Mount_Handler(srv interface{}, ctx context.Context, dec func(interface{}) error, interceptor grpc.UnaryServerInterceptor) (interface{}, error) {
	in := new(MountRequest)
	if err := dec(in); err != nil {
		return nil, err
	}
	if interceptor == nil {
		return srv.(FileSystemServiceServer).Mount(ctx, in)
	}
	info := &grpc.UnaryServerInfo{
		Server:     srv,
		FullMethod: "/io.typestream.grpc.FileSystemService/Mount",
	}
	handler := func(ctx context.Context, req interface{}) (interface{}, error) {
		return srv.(FileSystemServiceServer).Mount(ctx, req.(*MountRequest))
	}
	return interceptor(ctx, in, info, handler)
}

func _FileSystemService_Unmount_Handler(srv interface{}, ctx context.Context, dec func(interface{}) error, interceptor grpc.UnaryServerInterceptor) (interface{}, error) {
	in := new(UnmountRequest)
	if err := dec(in); err != nil {
		return nil, err
	}
	if interceptor == nil {
		return srv.(FileSystemServiceServer).Unmount(ctx, in)
	}
	info := &grpc.UnaryServerInfo{
		Server:     srv,
		FullMethod: "/io.typestream.grpc.FileSystemService/Unmount",
	}
	handler := func(ctx context.Context, req interface{}) (interface{}, error) {
		return srv.(FileSystemServiceServer).Unmount(ctx, req.(*UnmountRequest))
	}
	return interceptor(ctx, in, info, handler)
}

// FileSystemService_ServiceDesc is the grpc.ServiceDesc for FileSystemService service.
// It's only intended for direct use with grpc.RegisterService,
// and not to be introspected or modified (even as a copy)
var FileSystemService_ServiceDesc = grpc.ServiceDesc{
	ServiceName: "io.typestream.grpc.FileSystemService",
	HandlerType: (*FileSystemServiceServer)(nil),
	Methods: []grpc.MethodDesc{
		{
			MethodName: "Mount",
			Handler:    _FileSystemService_Mount_Handler,
		},
		{
			MethodName: "Unmount",
			Handler:    _FileSystemService_Unmount_Handler,
		},
	},
	Streams:  []grpc.StreamDesc{},
	Metadata: "filesystem.proto",
}
