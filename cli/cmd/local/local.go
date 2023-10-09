package local

import (
	"github.com/spf13/cobra"
)

var localCmd = &cobra.Command{
	Use:   "local",
	Short: "Manage a local TypeStream server",
}

func NewCommand() *cobra.Command {
	return localCmd
}
