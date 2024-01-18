package cmd

import (
	"context"
	"fmt"
	"os"
	"time"

	"github.com/spf13/cobra"
	"github.com/typestreamio/typestream/cli/pkg/grpc"
)

// unmountCmd represents the unmount command
var unmountCmd = &cobra.Command{
	Use:   "unmount",
	Short: "Unmounts an endpoint from a TypeStream server",
	Args:  cobra.ExactArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		client := grpc.NewClient()

		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Minute)
		defer cancel()
		defer client.Close()

		res, err := client.Unmount(ctx, args[0])
		if err != nil {
			fmt.Printf("💥 failed to unmount: %v\n", err)
			os.Exit(1)
		}

		if !res.Success {
			fmt.Printf("💥 failed to unmount: %s\n", res.Error)
			os.Exit(1)
		}

		fmt.Printf("🚀 %s unmounted successfully\n", args[0])
	},
}

func init() {
	rootCmd.AddCommand(unmountCmd)
}
