package k8s

import (
	_ "embed"

	"github.com/charmbracelet/log"
	"github.com/spf13/cobra"
	"github.com/typestreamio/typestream/cli/pkg/k8s"
)

var redpanda bool

var createCmd = &cobra.Command{
	Use:   "create",
	Short: "Creates a k8s TypeStream server",
	Run: func(cmd *cobra.Command, args []string) {
		log.Info("🚀 creating TypeStream server")
		runner := k8s.NewRunner()

		err := runner.Apply(redpanda)
		if err != nil {
			log.Fatal("💥 failed to apply resources: ", err)
		}

		log.Info("🎉 TypeStream server created")
	},
}

func init() {
	createCmd.Flags().BoolVarP(&redpanda, "redpanda", "r", false, "include redpanda in the deployment")
	k8sCmd.AddCommand(createCmd)
}
