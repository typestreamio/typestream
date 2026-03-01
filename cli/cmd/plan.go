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

var planCmd = &cobra.Command{
	Use:   "plan [file|dir]",
	Short: "Shows what changes 'apply' would make without applying them",
	Args:  cobra.ExactArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		path := args[0]

		info, err := os.Stat(path)
		if err != nil {
			fmt.Printf("error reading path: %v\n", err)
			os.Exit(1)
		}
		filterDeletes := !info.IsDir()

		files, err := resolvePipelineFiles(path)
		if err != nil {
			fmt.Printf("%v\n", err)
			os.Exit(1)
		}

		plans, err := loadPipelinePlans(files)
		if err != nil {
			fmt.Printf("%v\n", err)
			os.Exit(1)
		}

		client := grpc.NewClient(ServerAddress())
		ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
		defer cancel()
		defer func() { _ = client.Close() }()

		resp, err := client.PlanPipelines(ctx, &pipeline_service.PlanPipelinesRequest{
			Pipelines: plans,
		})
		if err != nil {
			fmt.Printf("error planning pipelines: %v\n", err)
			os.Exit(1)
		}

		if len(resp.Errors) > 0 {
			fmt.Println("errors:")
			for _, e := range resp.Errors {
				fmt.Printf("  - %s\n", e)
			}
		}

		if len(resp.Results) == 0 {
			fmt.Println("no changes")
			return
		}

		printPlanTable(os.Stdout, resp.Results, filterDeletes)
	},
}

func init() {
	rootCmd.AddCommand(planCmd)
}
