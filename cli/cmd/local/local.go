package local

import (
	"context"
	"time"

	"github.com/charmbracelet/log"
	"github.com/spf13/cobra"
	"github.com/typestreamio/typestream/cli/pkg/grpc"
)

var localCmd = &cobra.Command{
	Use:   "local",
	Short: "Manage a local TypeStream server",
	Run: func(cmd *cobra.Command, args []string) {
		client := grpc.NewClient()

		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Minute)
		defer cancel()

		if err := client.CheckConn(ctx); err != nil {
			log.Errorf("💥 Failed to connect to TypeStream server: %v", err)
			log.Info("ℹ️  Try running `typestream local start`")
		} else {
			log.Info("🎉 TypeStream server is running")
		}
	},
}

func NewCommand() *cobra.Command {
	return localCmd
}
