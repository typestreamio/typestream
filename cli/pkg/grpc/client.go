package grpc

import (
	"context"
	"os"

	"github.com/charmbracelet/log"
	"github.com/typestreamio/typestream/cli/pkg/interactive_session_service"
	"github.com/typestreamio/typestream/cli/pkg/job_service"

	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
)

type Client struct {
	conn *grpc.ClientConn
}

func NewClient() *Client {
	var opts []grpc.DialOption

	opts = append(opts, grpc.WithTransportCredentials(insecure.NewCredentials()))

	host := os.Getenv("TYPESTREAM_HOST")
	port := os.Getenv("TYPESTREAM_PORT")
	if host == "" {
		host = "127.0.0.1"
	}
	if port == "" {
		port = "4242"
	}

	conn, err := grpc.Dial(host+":"+port, opts...)
	if err != nil {
		log.Fatalf("💥 cannot connect to TypeStream server %v\n", err)
	}
	return &Client{
		conn: conn,
	}
}

func (c *Client) StartSession(ctx context.Context, req *interactive_session_service.StartSessionRequest) (*interactive_session_service.StartSessionResponse, error) {
	client := interactive_session_service.NewInteractiveSessionServiceClient(c.conn)

	return client.StartSession(ctx, req)
}

func (c *Client) RunProgram(ctx context.Context, req *interactive_session_service.RunProgramRequest) (*interactive_session_service.RunProgramResponse, error) {
	client := interactive_session_service.NewInteractiveSessionServiceClient(c.conn)

	return client.RunProgram(ctx, req)
}

func (c *Client) GetProgramOutput(ctx context.Context, req *interactive_session_service.GetProgramOutputRequest) (interactive_session_service.InteractiveSessionService_GetProgramOutputClient, error) {
	client := interactive_session_service.NewInteractiveSessionServiceClient(c.conn)
	return client.GetProgramOutput(ctx, req)
}

func (c *Client) CompleteProgram(ctx context.Context, req *interactive_session_service.CompleteProgramRequest) (*interactive_session_service.CompleteProgramResponse, error) {
	client := interactive_session_service.NewInteractiveSessionServiceClient(c.conn)

	return client.CompleteProgram(ctx, req)
}

func (c *Client) CreateJob(ctx context.Context, req *job_service.CreateJobRequest) (*job_service.CreateJobResponse, error) {
	client := job_service.NewJobServiceClient(c.conn)

	return client.CreateJob(ctx, req)
}

func (c *Client) StopSession(ctx context.Context, req *interactive_session_service.StopSessionRequest) (*interactive_session_service.StopSessionResponse, error) {
	client := interactive_session_service.NewInteractiveSessionServiceClient(c.conn)

	return client.StopSession(ctx, req)
}

func (c *Client) Close() error {
	return c.conn.Close()
}
