package cmd

import (
	"context"
	"fmt"
	"os"
	"time"

	"github.com/spf13/cobra"
	"github.com/typestreamio/typestream/cli/pkg/grpc"
)

// mountCmd represents the mount command
var mountCmd = &cobra.Command{
	Use:   "mount",
	Short: "Mounts endpoints to a TypeStream server",
	Args:  cobra.ExactArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		client := grpc.NewClient()

		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Minute)
		defer cancel()
		defer client.Close()

		config := readFile(args[0])

		res, err := client.Mount(ctx, config)
		if err != nil {
			fmt.Printf("💥 failed to mount: %v\n", err)
			os.Exit(1)
		}

		if !res.Success {
			fmt.Printf("💥 failed to mount: %s\n", res.Error)
			os.Exit(1)
		}

		fmt.Printf("🚀 %s mounted successfully\n", args[0])
	},
}

func init() {
	rootCmd.AddCommand(mountCmd)
}
