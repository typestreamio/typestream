package local

import (
	"fmt"

	"github.com/spf13/cobra"
	"github.com/typestreamio/typestream/cli/pkg/compose"
)

var showCmd = &cobra.Command{
	Use:   "show",
	Short: "Shows the local docker compose file",
	Run: func(cmd *cobra.Command, args []string) {
		runner := compose.NewRunner()

		fmt.Println(runner.Show())
	},
}

func init() {
	localCmd.AddCommand(showCmd)
}
