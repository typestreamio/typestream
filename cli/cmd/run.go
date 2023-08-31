/*
Copyright © 2023 NAME HERE <EMAIL ADDRESS>
*/
package cmd

import (
	"context"
	"time"

	"github.com/charmbracelet/log"
	"github.com/spf13/cobra"
	"github.com/typestreamio/typestream/cli/pkg/grpc"
	"github.com/typestreamio/typestream/cli/pkg/program_service"
)

// runCmd represents the run command
var runCmd = &cobra.Command{
	Use:   "run",
	Short: "Runs a command on a TypeStream server and displays the output",
	Args:  cobra.ExactArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		client := grpc.NewClient()

		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Minute)
		defer cancel()

		defer client.Close()

		if err := client.CheckConn(ctx); err != nil {
			log.Fatalf("💥 cannot connect to TypeStream server %v", err)
		}

		runProgramResponse, err := client.RunProgram(ctx, &program_service.RunProgramRequest{Source: args[0]})
		if err != nil {
			log.Fatalf("💥 failed to run program: %v", err)
		}
		if runProgramResponse.StdOut != "" {
			println(runProgramResponse.StdOut)
		}
	},
}

func init() {
	rootCmd.AddCommand(runCmd)
}
