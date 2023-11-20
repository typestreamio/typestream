package k8s

import (
	"github.com/charmbracelet/log"
	"github.com/spf13/cobra"
	"github.com/typestreamio/typestream/cli/pkg/k8s"
)

// seedCmd represents the seed command
var seedCmd = &cobra.Command{
	Use:   "seed",
	Short: "Seeds the local TypeStream server with some data",
	Run: func(cmd *cobra.Command, args []string) {
		log.Info("🚀 starting seeding process")
		runner := k8s.NewRunner(Namespace)

		err := runner.ApplySeeder()
		if err != nil {
			log.Fatal("💥 failed to apply resources: ", err)
		}

		log.Info("✅ seeding done")
	},
}

func init() {
	k8sCmd.AddCommand(seedCmd)
}
