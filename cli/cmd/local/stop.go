package local

import (
	"github.com/charmbracelet/log"
	"github.com/spf13/cobra"
	"github.com/typestreamio/typestream/cli/pkg/compose"
)

// stopCmd represents the stop command
var stopCmd = &cobra.Command{
	Use:   "stop",
	Short: "Stops a TypeStream server",
	Run: func(cmd *cobra.Command, args []string) {
		log.Info("✋ Stopping TypeStream server")
		runner := compose.NewRunner()
		go func() {
			for m := range runner.StdOut {
				log.Info(m)
			}
		}()
		err := runner.RunCommand("down")
		if err != nil {
			log.Fatalf("Failed to stop Docker Compose project: %v", err)
		}

		log.Info("👋 TypeStream server stopped")
	},
}

func init() {
	localCmd.AddCommand(stopCmd)
}
