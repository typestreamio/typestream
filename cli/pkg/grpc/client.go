package grpc

import (
	"context"
	"os"

	"github.com/charmbracelet/log"
	"github.com/typestreamio/typestream/cli/pkg/filesystem_service"
	"github.com/typestreamio/typestream/cli/pkg/interactive_session_service"
	"github.com/typestreamio/typestream/cli/pkg/job_service"
	"github.com/typestreamio/typestream/cli/pkg/pipeline_service"

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

	conn, err := grpc.NewClient(host+":"+port, opts...)
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

func (c *Client) Mount(ctx context.Context, config string) (*filesystem_service.MountResponse, error) {
	client := filesystem_service.NewFileSystemServiceClient(c.conn)

	// TODO get from config
	return client.Mount(ctx, &filesystem_service.MountRequest{Config: config, UserId: "42"})
}

func (c *Client) Unmount(ctx context.Context, endpoint string) (*filesystem_service.UnmountResponse, error) {
	client := filesystem_service.NewFileSystemServiceClient(c.conn)

	// TODO get from config
	return client.Unmount(ctx, &filesystem_service.UnmountRequest{Endpoint: endpoint, UserId: "42"})
}

func (c *Client) ValidatePipeline(ctx context.Context, req *pipeline_service.ValidatePipelineRequest) (*pipeline_service.ValidatePipelineResponse, error) {
	client := pipeline_service.NewPipelineServiceClient(c.conn)
	return client.ValidatePipeline(ctx, req)
}

func (c *Client) ApplyPipeline(ctx context.Context, req *pipeline_service.ApplyPipelineRequest) (*pipeline_service.ApplyPipelineResponse, error) {
	client := pipeline_service.NewPipelineServiceClient(c.conn)
	return client.ApplyPipeline(ctx, req)
}

func (c *Client) ListPipelines(ctx context.Context, req *pipeline_service.ListPipelinesRequest) (*pipeline_service.ListPipelinesResponse, error) {
	client := pipeline_service.NewPipelineServiceClient(c.conn)
	return client.ListPipelines(ctx, req)
}

func (c *Client) DeletePipeline(ctx context.Context, req *pipeline_service.DeletePipelineRequest) (*pipeline_service.DeletePipelineResponse, error) {
	client := pipeline_service.NewPipelineServiceClient(c.conn)
	return client.DeletePipeline(ctx, req)
}

func (c *Client) PlanPipelines(ctx context.Context, req *pipeline_service.PlanPipelinesRequest) (*pipeline_service.PlanPipelinesResponse, error) {
	client := pipeline_service.NewPipelineServiceClient(c.conn)
	return client.PlanPipelines(ctx, req)
}

func (c *Client) Close() error {
	return c.conn.Close()
}
