package grpc

import (
	"context"

	"github.com/charmbracelet/log"
	"github.com/typestreamio/typestream/cli/pkg/program_service"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
)

type Client struct {
	conn *grpc.ClientConn
}

func NewClient() *Client {
	var opts []grpc.DialOption

	opts = append(opts, grpc.WithTransportCredentials(insecure.NewCredentials()))

	conn, err := grpc.Dial("127.0.0.1:4242", opts...)
	if err != nil {
		log.Fatalf("💥 cannot connect to TypeStream server %v\n", err)
	}
	return &Client{
		conn: conn,
	}
}

func (c *Client) RunProgram(ctx context.Context, req *program_service.RunProgramRequest) (*program_service.RunProgramResponse, error) {
	client := program_service.NewProgramServiceClient(c.conn)

	return client.RunProgram(ctx, req)
}

func (c *Client) GetProgramOutput(ctx context.Context, req *program_service.GetProgramOutputRequest) (program_service.ProgramService_GetProgramOutputClient, error) {
	client := program_service.NewProgramServiceClient(c.conn)
	return client.GetProgramOutput(ctx, req)
}

func (c *Client) CheckConn(ctx context.Context) error {
	_, err := c.RunProgram(ctx, &program_service.RunProgramRequest{Source: "ls"})

	return err
}

func (c *Client) CompleteProgram(ctx context.Context, req *program_service.CompleteProgramRequest) (*program_service.CompleteProgramResponse, error) {
	client := program_service.NewProgramServiceClient(c.conn)

	return client.CompleteProgram(ctx, req)
}

func (c *Client) Close() error {
	return c.conn.Close()
}
