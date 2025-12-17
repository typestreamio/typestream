package local

import (
	_ "embed"
	"regexp"
	"strings"

	"github.com/charmbracelet/log"
	"github.com/spf13/cobra"
	"github.com/typestreamio/typestream/cli/pkg/compose"
)

var devCreating = regexp.MustCompile(`Container typestream-dev-(.*)-1  Creating`)
var devStarted = regexp.MustCompile(`Container typestream-dev-(.*)-1  Started`)
var devHealthy = regexp.MustCompile(`Container typestream-dev-(.*)-1  Healthy`)

var devStopCmd = &cobra.Command{
	Use:   "stop",
	Short: "Stop development mode services",
	Run: func(cmd *cobra.Command, args []string) {
		log.Info("✋ Stopping development services")
		runner := compose.NewDevRunner()
		go func() {
			for m := range runner.StdOut {
				log.Info(m)
			}
		}()
		err := runner.RunCommand("down")
		if err != nil {
			log.Fatalf("💥 failed to stop dev services: %v", err)
		}
		log.Info("👋 Development services stopped")
	},
}

var devCmd = &cobra.Command{
	Use:   "dev",
	Short: "Start TypeStream in development mode (dependencies only)",
	Long: `Starts only the infrastructure services (Redpanda, Envoy, UI) in Docker.
The TypeStream server should be run separately on the host for fast iteration.

After running this command:
1. In another terminal, run: ./gradlew server:run --continuous
2. Edit Kotlin files and watch them reload automatically (~5s)

Or use the helper script: ./scripts/dev/server.sh

To stop dev services: ./typestream local dev stop`,
	Run: func(cmd *cobra.Command, args []string) {
		log.Info("🚀 starting TypeStream in development mode")
		log.Info("📦 starting infrastructure services (Redpanda, Envoy, UI)")

		runner := compose.NewDevRunner()
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

				if devCreating.MatchString(m) {
					capture := devCreating.FindStringSubmatch(m)
					log.Info("🛫 starting " + capture[1])
				}

				if devStarted.MatchString(m) {
					capture := devStarted.FindStringSubmatch(m)
					log.Info("✨ " + capture[1] + " started")
				}

				if devHealthy.MatchString(m) {
					capture := devHealthy.FindStringSubmatch(m)
					log.Info("✅ " + capture[1] + " healthy")
				}
			}
		}()

		err := runner.RunCommand("up", "--detach", "--wait", "--force-recreate", "--remove-orphans")
		if err != nil {
			log.Fatalf("💥 failed to run docker compose: %v", err)
		}

		log.Info("✅ infrastructure ready")
		log.Info("")
		log.Info("📝 Next steps:")
		log.Info("   1. Run the server: ./gradlew server:run --continuous")
		log.Info("   2. Or use the script: ./scripts/dev/server.sh")
		log.Info("   3. Edit Kotlin files and watch auto-reload!")
		log.Info("")
		log.Info("🌐 Services:")
		log.Info("   • React UI:     http://localhost:5173")
		log.Info("   • Envoy Proxy:  http://localhost:8080")
		log.Info("   • Kafbat UI:    http://localhost:8088")
		log.Info("   • Kafka:        localhost:19092")
		log.Info("   • Schema Reg:   http://localhost:18081")
	},
}

func init() {
	devCmd.AddCommand(devStopCmd)
	localCmd.AddCommand(devCmd)
}
