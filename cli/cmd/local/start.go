package local

import (
	_ "embed"
	"regexp"
	"strings"

	"github.com/charmbracelet/log"
	"github.com/spf13/cobra"
	"github.com/typestreamio/typestream/cli/pkg/compose"
)

var creating = regexp.MustCompile(`Container typestream-(.*)-1  Creating`)
var started = regexp.MustCompile(`Container typestream-(.*)-1  Started`)
var healthy = regexp.MustCompile(`Container typestream-(.*)-1  Healthy`)

var startCmd = &cobra.Command{
	Use:   "start",
	Short: "Starts a TypeStream server",
	Run: func(cmd *cobra.Command, args []string) {
		log.Info("🚀 starting TypeStream server")
		runner := compose.NewRunner()
		go func() {
			log.Info("🐳 starting docker compose")
			for m := range runner.StdOut {
				if strings.Contains(m, "Error response from daemon") {
					log.Error("💥 " + m)
				}
				if strings.Contains(m, "redpanda Pulling") {
					log.Info("📦 downloading redpanda")
					log.Info("⏳ this may take a while...")
				}
				if strings.Contains(m, "redpanda Pulled") {
					log.Info("✅ redpanda downloaded")
				}

				if creating.MatchString(m) {
					capture := creating.FindStringSubmatch(m)
					log.Info("🛫 starting " + capture[1])
				}

				if started.MatchString(m) {
					capture := started.FindStringSubmatch(m)
					log.Info("✨ " + capture[1] + " started")
				}

				if healthy.MatchString(m) {
					capture := healthy.FindStringSubmatch(m)
					log.Info("✅ " + capture[1] + " healthy")
				}
			}
		}()
		err := runner.RunCommand("up", "--detach", "--wait")
		if err != nil {
			log.Fatalf("💥 failed to run docker compose: %v", err)
		}

		log.Info("🎉 TypeStream server started")
	},
}

func init() {
	localCmd.AddCommand(startCmd)
}
