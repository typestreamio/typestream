package cmd

import (
	"context"
	"fmt"
	"os"
	"time"

	"github.com/spf13/cobra"
	"github.com/typestreamio/typestream/cli/pkg/grpc"
	"github.com/typestreamio/typestream/cli/pkg/pipeline_service"
)

var applyCmd = &cobra.Command{
	Use:   "apply [file]",
	Short: "Applies a pipeline definition to the server",
	Args:  cobra.ExactArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		content, err := os.ReadFile(args[0])
		if err != nil {
			fmt.Printf("error reading file: %v\n", err)
			os.Exit(1)
		}

		metadata, graph, err := parsePipelineFile(content)
		if err != nil {
			fmt.Printf("error parsing pipeline file: %v\n", err)
			os.Exit(1)
		}

		client := grpc.NewClient()
		ctx, cancel := context.WithTimeout(context.Background(), 2*time.Minute)
		defer cancel()
		defer func() { _ = client.Close() }()

		resp, err := client.ApplyPipeline(ctx, &pipeline_service.ApplyPipelineRequest{
			Metadata: metadata,
			Graph:    graph,
		})
		if err != nil {
			fmt.Printf("error applying pipeline: %v\n", err)
			os.Exit(1)
		}

		if !resp.Success {
			fmt.Printf("failed to apply pipeline '%s': %s\n", metadata.Name, resp.Error)
			os.Exit(1)
		}

		switch resp.State {
		case pipeline_service.PipelineState_CREATED:
			fmt.Printf("pipeline '%s' created (job: %s)\n", metadata.Name, resp.JobId)
		case pipeline_service.PipelineState_UPDATED:
			fmt.Printf("pipeline '%s' updated (job: %s)\n", metadata.Name, resp.JobId)
		case pipeline_service.PipelineState_UNCHANGED:
			fmt.Printf("pipeline '%s' unchanged (job: %s)\n", metadata.Name, resp.JobId)
		default:
			fmt.Printf("pipeline '%s' applied (job: %s)\n", metadata.Name, resp.JobId)
		}
	},
}

func init() {
	rootCmd.AddCommand(applyCmd)
}
