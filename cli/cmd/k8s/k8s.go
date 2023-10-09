package k8s

import (
	"github.com/spf13/cobra"
)

var k8sCmd = &cobra.Command{
	Use:   "k8s",
	Short: "Manage a kubernetes TypeStream server",
}

func NewCommand() *cobra.Command {
	return k8sCmd
}
