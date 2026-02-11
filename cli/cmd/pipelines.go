package cmd

import (
	"context"
	"fmt"
	"os"
	"text/tabwriter"
	"time"

	"github.com/spf13/cobra"
	"github.com/typestreamio/typestream/cli/pkg/grpc"
	"github.com/typestreamio/typestream/cli/pkg/pipeline_service"
)

var pipelinesCmd = &cobra.Command{
	Use:   "pipelines",
	Short: "Manage pipelines",
}

var pipelinesListCmd = &cobra.Command{
	Use:   "list",
	Short: "Lists all managed pipelines",
	Run: func(cmd *cobra.Command, args []string) {
		client := grpc.NewClient()
		ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
		defer cancel()
		defer func() { _ = client.Close() }()

		resp, err := client.ListPipelines(ctx, &pipeline_service.ListPipelinesRequest{})
		if err != nil {
			fmt.Printf("error listing pipelines: %v\n", err)
			os.Exit(1)
		}

		if len(resp.Pipelines) == 0 {
			fmt.Println("no pipelines found")
			return
		}

		w := tabwriter.NewWriter(os.Stdout, 0, 0, 2, ' ', 0)
		fmt.Fprintln(w, "NAME\tVERSION\tJOB ID\tSTATE\tDESCRIPTION")
		for _, p := range resp.Pipelines {
			fmt.Fprintf(w, "%s\t%s\t%s\t%s\t%s\n",
				p.Name,
				p.Version,
				p.JobId,
				p.JobState.String(),
				p.Description,
			)
		}
		w.Flush()
	},
}

var pipelinesDeleteCmd = &cobra.Command{
	Use:   "delete [name]",
	Short: "Deletes a managed pipeline",
	Args:  cobra.ExactArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		client := grpc.NewClient()
		ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
		defer cancel()
		defer func() { _ = client.Close() }()

		resp, err := client.DeletePipeline(ctx, &pipeline_service.DeletePipelineRequest{
			Name: args[0],
		})
		if err != nil {
			fmt.Printf("error deleting pipeline: %v\n", err)
			os.Exit(1)
		}

		if !resp.Success {
			fmt.Printf("failed to delete pipeline '%s': %s\n", args[0], resp.Error)
			os.Exit(1)
		}

		fmt.Printf("pipeline '%s' deleted\n", args[0])
	},
}

func init() {
	pipelinesCmd.AddCommand(pipelinesListCmd)
	pipelinesCmd.AddCommand(pipelinesDeleteCmd)
	rootCmd.AddCommand(pipelinesCmd)
}
