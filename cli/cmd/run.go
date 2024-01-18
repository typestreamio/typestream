package cmd

import (
	"context"
	"fmt"
	"os"
	"time"

	"github.com/spf13/cobra"
	"github.com/typestreamio/typestream/cli/pkg/grpc"
	"github.com/typestreamio/typestream/cli/pkg/job_service"
)

func readFile(arg string) string {
	stat, err := os.Stat(arg)
	if err == nil && !stat.IsDir() {
		content, err := os.ReadFile(arg)
		if err != nil {
			fmt.Println("error reading file:", err)
			os.Exit(1)
		}
		return string(content)
	}

	return arg
}

// runCmd represents the run command
var runCmd = &cobra.Command{
	Use:   "run",
	Short: "Runs a pipeline on a TypeStream server",
	Args:  cobra.ExactArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		client := grpc.NewClient()

		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Minute)
		defer cancel()

		defer client.Close()

		source := readFile(args[0])

		createJobResponse, err := client.CreateJob(ctx, &job_service.CreateJobRequest{
			UserId: "42", // TODO get from config
			Source: source,
		})
		if err != nil {
			fmt.Printf("💥 failed to create job: %v\n", err)
			os.Exit(1)
		}

		if !createJobResponse.Success {
			fmt.Printf("💥 failed to create job: %s\n", createJobResponse.Error)
			os.Exit(1)
		}

		fmt.Printf("🚀 job created: %s\n", createJobResponse.JobId)
	},
}

func init() {
	rootCmd.AddCommand(runCmd)
}
