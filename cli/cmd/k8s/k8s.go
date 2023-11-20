package k8s

import (
	"fmt"

	"github.com/spf13/cobra"
	"github.com/typestreamio/typestream/cli/pkg/k8s"
)

var Namespace string

var k8sCmd = &cobra.Command{
	Use:   "k8s",
	Short: "Manage a kubernetes TypeStream server",
	Run: func(cmd *cobra.Command, args []string) {
		runner := k8s.NewRunner(Namespace)

		fmt.Println(runner.Show())
	},
}

func NewCommand() *cobra.Command {
	return k8sCmd
}

func init() {
	k8sCmd.PersistentFlags().StringVarP(&Namespace, "namespace", "n", "typestream", "kubernetes namespace to use")
}
