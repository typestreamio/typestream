package cmd

import (
	"context"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"text/tabwriter"
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

		var files []string
		if info.IsDir() {
			entries, err := os.ReadDir(path)
			if err != nil {
				fmt.Printf("error reading directory: %v\n", err)
				os.Exit(1)
			}
			for _, entry := range entries {
				if !entry.IsDir() && strings.HasSuffix(entry.Name(), ".typestream.json") {
					files = append(files, filepath.Join(path, entry.Name()))
				}
			}
			if len(files) == 0 {
				fmt.Println("no .typestream.json files found in directory")
				os.Exit(1)
			}
		} else {
			files = []string{path}
		}

		var plans []*pipeline_service.PipelinePlan
		for _, f := range files {
			content, err := os.ReadFile(f)
			if err != nil {
				fmt.Printf("error reading file %s: %v\n", f, err)
				os.Exit(1)
			}

			metadata, graph, err := parsePipelineFile(content)
			if err != nil {
				fmt.Printf("error parsing %s: %v\n", f, err)
				os.Exit(1)
			}

			plans = append(plans, &pipeline_service.PipelinePlan{
				Metadata: metadata,
				Graph:    graph,
			})
		}

		client := grpc.NewClient()
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

		w := tabwriter.NewWriter(os.Stdout, 0, 0, 2, ' ', 0)
		_, _ = fmt.Fprintln(w, "NAME\tACTION\tCURRENT VERSION\tNEW VERSION")
		for _, r := range resp.Results {
			action := formatAction(r.Action)
			_, _ = fmt.Fprintf(w, "%s\t%s\t%s\t%s\n",
				r.Name,
				action,
				r.CurrentVersion,
				r.NewVersion,
			)
		}
		_ = w.Flush()
	},
}

func formatAction(action pipeline_service.PipelineAction) string {
	switch action {
	case pipeline_service.PipelineAction_CREATE:
		return "\033[32m+ create\033[0m"
	case pipeline_service.PipelineAction_UPDATE:
		return "\033[33m~ update\033[0m"
	case pipeline_service.PipelineAction_NO_CHANGE:
		return "\033[2m  unchanged\033[0m"
	case pipeline_service.PipelineAction_DELETE:
		return "\033[31m- delete\033[0m"
	default:
		return "  unknown"
	}
}

func init() {
	rootCmd.AddCommand(planCmd)
}
