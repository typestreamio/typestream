package local

import (
	"regexp"
	"strings"

	"github.com/charmbracelet/log"
	"github.com/spf13/cobra"
	"github.com/typestreamio/typestream/cli/pkg/compose"
)

var devBuilding = regexp.MustCompile(`typestream-(.*)\s+Building`)
var devBuilt = regexp.MustCompile(`typestream-(.*)\s+Built`)
var devCreating = regexp.MustCompile(`Container typestream-(.*)-1  Creating`)
var devStarted = regexp.MustCompile(`Container typestream-(.*)-1  Started`)
var devHealthy = regexp.MustCompile(`Container typestream-(.*)-1  Healthy`)

var devStopCmd = &cobra.Command{
	Use:   "stop",
	Short: "Stop development mode services",
	Run: func(cmd *cobra.Command, args []string) {
		log.Info("Stopping development services")
		runner := compose.NewDevRunner()
		go func() {
			for m := range runner.StdOut {
				log.Info(m)
			}
		}()
		err := runner.RunCommand("down")
		if err != nil {
			log.Fatalf("Failed to stop dev services: %v", err)
		}
		log.Info("Development services stopped")
	},
}

var devCleanCmd = &cobra.Command{
	Use:   "clean",
	Short: "Stop services, remove volumes, and purge built images",
	Long: `Stops all development services, removes Docker volumes, and deletes locally-built images.
This will delete all Kafka topics, schemas, and any other persisted data.
Built images (demo-data, kafka-connect) will be removed to force a fresh rebuild.
Use this for a completely fresh start.`,
	Run: func(cmd *cobra.Command, args []string) {
		log.Info("Stopping services, removing volumes, and purging images")
		runner := compose.NewDevRunner()
		go func() {
			for m := range runner.StdOut {
				log.Info(m)
			}
		}()
		err := runner.RunCommand("down", "--volumes", "--remove-orphans", "--rmi", "local")
		if err != nil {
			log.Fatalf("Failed to clean dev services: %v", err)
		}
		log.Info("Development services stopped, volumes removed, and images purged")
	},
}

var devCmd = &cobra.Command{
	Use:   "dev",
	Short: "Start TypeStream in development mode (dependencies only)",
	Long: `Starts only the infrastructure services (Redpanda, Envoy, UI) in Docker.
The TypeStream server should be run separately on the host for fast iteration.

After running this command:
1. In another terminal, run: ./scripts/dev/server.sh
2. Edit Kotlin files and watch them reload automatically (~5s)

To stop dev services: ./typestream local dev stop`,
	Run: func(cmd *cobra.Command, args []string) {
		log.Info("Starting TypeStream in development mode")
		log.Info("Starting infrastructure services (Redpanda, Envoy, UI)")

		runner := compose.NewDevRunner()
		go func() {
			log.Info("Starting docker compose")
			for m := range runner.StdOut {
				if strings.Contains(m, "Error response from daemon") {
					log.Error(m)
				}
				if strings.Contains(m, "redpanda Pulling") {
					log.Info("Downloading redpanda")
					log.Info("This may take a while...")
				}
				if strings.Contains(m, "redpanda Pulled") {
					log.Info("Redpanda downloaded")
				}

				if devBuilding.MatchString(m) {
					capture := devBuilding.FindStringSubmatch(m)
					log.Info("Building " + capture[1] + " image...")
				}

				if devBuilt.MatchString(m) {
					capture := devBuilt.FindStringSubmatch(m)
					log.Info(capture[1] + " image built")
				}

				if devCreating.MatchString(m) {
					capture := devCreating.FindStringSubmatch(m)
					log.Info("Starting " + capture[1])
				}

				if devStarted.MatchString(m) {
					capture := devStarted.FindStringSubmatch(m)
					log.Info(capture[1] + " started")
				}

				if devHealthy.MatchString(m) {
					capture := devHealthy.FindStringSubmatch(m)
					log.Info(capture[1] + " healthy")
				}
			}
		}()

		err := runner.RunCommand("up", "--detach", "--wait", "--remove-orphans")
		if err != nil {
			log.Fatalf("Failed to run docker compose: %v", err)
		}

		log.Info("Infrastructure ready")
		log.Info("")
		log.Info("Next steps:")
		log.Info("   1. Run the server: ./scripts/dev/server.sh")
		log.Info("   2. Edit Kotlin files and watch auto-reload!")
		log.Info("")
		log.Info("Services:")
		log.Info("   React UI:     http://localhost:5173")
		log.Info("   Envoy Proxy:  http://localhost:8080")
		log.Info("   Kafbat UI:    http://localhost:8088")
		log.Info("   Kafka:        localhost:19092")
		log.Info("   Schema Reg:   http://localhost:18081")
	},
}

func init() {
	devCmd.AddCommand(devStopCmd)
	devCmd.AddCommand(devCleanCmd)
	localCmd.AddCommand(devCmd)
}
