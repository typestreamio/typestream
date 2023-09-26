package cmd

import (
	"os"

	"github.com/spf13/cobra"
	"github.com/typestreamio/typestream/cli/cmd/k8s"
	"github.com/typestreamio/typestream/cli/cmd/local"
	"github.com/typestreamio/typestream/cli/pkg/shell"
	"github.com/typestreamio/typestream/cli/pkg/version"
)

var rootCmd = &cobra.Command{
	Use:   "typestream",
	Short: "TypeStream is an abstraction layer for streaming data",
	Long:  `TypeStream allows you to write typed streaming pipeline with a familiar syntax.`,
	Run: func(cmd *cobra.Command, args []string) {
		shell.Run()
	},
}

func Execute() {
	err := rootCmd.Execute()
	if err != nil {
		os.Exit(1)
	}
}

func init() {
	rootCmd.AddCommand(k8s.NewCommand())
	rootCmd.AddCommand(local.NewCommand())
	rootCmd.Version = version.BuildVersion()
}
