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

var applyPlanFlag bool

var applyCmd = &cobra.Command{
	Use:   "apply [file|dir]",
	Short: "Applies pipeline definitions to the server",
	Args:  cobra.ExactArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		path := args[0]

		info, err := os.Stat(path)
		if err != nil {
			fmt.Printf("error reading path: %v\n", err)
			os.Exit(1)
		}
		isDir := info.IsDir()

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
		ctx, cancel := context.WithTimeout(context.Background(), 2*time.Minute)
		defer cancel()
		defer func() { _ = client.Close() }()

		if isDir {
			applyDirectory(ctx, client, plans, !isDir)
		} else {
			applySingleFile(ctx, client, plans[0], !isDir)
		}
	},
}

func applySingleFile(ctx context.Context, client *grpc.Client, plan *pipeline_service.PipelinePlan, filterDeletes bool) {
	if applyPlanFlag {
		resp, err := client.PlanPipelines(ctx, &pipeline_service.PlanPipelinesRequest{
			Pipelines: []*pipeline_service.PipelinePlan{plan},
		})
		if err != nil {
			fmt.Printf("error planning pipeline: %v\n", err)
			os.Exit(1)
		}

		if len(resp.Errors) > 0 {
			fmt.Println("errors:")
			for _, e := range resp.Errors {
				fmt.Printf("  - %s\n", e)
			}
		}

		if allNoChange(resp.Results) {
			fmt.Println("no changes")
			return
		}

		printPlanTable(os.Stdout, resp.Results, filterDeletes)
		if !confirmApply(os.Stdin) {
			fmt.Println("apply cancelled")
			return
		}
	}

	resp, err := client.ApplyPipeline(ctx, &pipeline_service.ApplyPipelineRequest{
		Metadata: plan.Metadata,
		Graph:    plan.Graph,
	})
	if err != nil {
		fmt.Printf("error applying pipeline: %v\n", err)
		os.Exit(1)
	}

	if !resp.Success {
		fmt.Printf("failed to apply pipeline '%s': %s\n", plan.Metadata.Name, resp.Error)
		os.Exit(1)
	}

	printApplyResult(plan.Metadata.Name, resp)
}

func applyDirectory(ctx context.Context, client *grpc.Client, plans []*pipeline_service.PipelinePlan, filterDeletes bool) {
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

	if allNoChange(resp.Results) {
		fmt.Println("no changes")
		return
	}

	if applyPlanFlag {
		printPlanTable(os.Stdout, resp.Results, filterDeletes)
		if !confirmApply(os.Stdin) {
			fmt.Println("apply cancelled")
			return
		}
	}

	// Build a map from plan name to plan for CREATE/UPDATE actions
	planByName := make(map[string]*pipeline_service.PipelinePlan)
	for _, p := range plans {
		planByName[p.Metadata.Name] = p
	}

	for _, r := range resp.Results {
		switch r.Action {
		case pipeline_service.PipelineAction_CREATE, pipeline_service.PipelineAction_UPDATE:
			plan, ok := planByName[r.Name]
			if !ok {
				fmt.Printf("error: plan not found for pipeline '%s'\n", r.Name)
				os.Exit(1)
			}
			applyResp, err := client.ApplyPipeline(ctx, &pipeline_service.ApplyPipelineRequest{
				Metadata: plan.Metadata,
				Graph:    plan.Graph,
			})
			if err != nil {
				fmt.Printf("error applying pipeline '%s': %v\n", r.Name, err)
				os.Exit(1)
			}
			if !applyResp.Success {
				fmt.Printf("failed to apply pipeline '%s': %s\n", r.Name, applyResp.Error)
				os.Exit(1)
			}
			printApplyResult(r.Name, applyResp)

		case pipeline_service.PipelineAction_DELETE:
			delResp, err := client.DeletePipeline(ctx, &pipeline_service.DeletePipelineRequest{
				Name: r.Name,
			})
			if err != nil {
				fmt.Printf("error deleting pipeline '%s': %v\n", r.Name, err)
				os.Exit(1)
			}
			if !delResp.Success {
				fmt.Printf("failed to delete pipeline '%s': %s\n", r.Name, delResp.Error)
				os.Exit(1)
			}
			fmt.Printf("pipeline '%s' deleted\n", r.Name)

		case pipeline_service.PipelineAction_NO_CHANGE:
			// skip
		}
	}
}

func allNoChange(results []*pipeline_service.PipelinePlanResult) bool {
	for _, r := range results {
		if r.Action != pipeline_service.PipelineAction_NO_CHANGE {
			return false
		}
	}
	return true
}

func printApplyResult(name string, resp *pipeline_service.ApplyPipelineResponse) {
	switch resp.State {
	case pipeline_service.PipelineState_CREATED:
		fmt.Printf("pipeline '%s' created (job: %s)\n", name, resp.JobId)
	case pipeline_service.PipelineState_UPDATED:
		fmt.Printf("pipeline '%s' updated (job: %s)\n", name, resp.JobId)
	case pipeline_service.PipelineState_UNCHANGED:
		fmt.Printf("pipeline '%s' unchanged (job: %s)\n", name, resp.JobId)
	default:
		fmt.Printf("pipeline '%s' applied (job: %s)\n", name, resp.JobId)
	}
}

func init() {
	applyCmd.Flags().BoolVar(&applyPlanFlag, "plan", false, "Show plan and confirm before applying")
	rootCmd.AddCommand(applyCmd)
}
