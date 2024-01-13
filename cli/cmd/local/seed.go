package local

import (
	"context"
	"fmt"
	"io"

	"github.com/charmbracelet/log"
	"github.com/docker/docker/api/types"
	"github.com/docker/docker/api/types/container"
	"github.com/docker/docker/client"
	"github.com/spf13/cobra"
	"github.com/typestreamio/typestream/cli/pkg/version"
)

const imgName = "typestream/tools-seeder"

const config = `
[grpc]
port=4242
[sources.kafka.local]
bootstrapServers="localhost:19092"
schemaRegistry.url="http://localhost:18081"
fsRefreshRate=10
`

// seedCmd represents the seed command
var seedCmd = &cobra.Command{
	Use:   "seed",
	Short: "Seeds the local TypeStream server with some data",
	Run: func(cmd *cobra.Command, args []string) {
		cli, err := client.NewClientWithOpts(client.FromEnv)
		if err != nil {
			log.Fatalf("💥 failed to create docker client: %v", err)
		}

		log.Info("📥 pulling image")
		log.Info("⏳ this may take a while...")
		ctx := context.Background()

		cli.NegotiateAPIVersion(ctx)
		image := version.DockerImage(imgName)
		out, err := cli.ImagePull(ctx, image, types.ImagePullOptions{})
		if err != nil {
			log.Fatalf("💥 image pull failed: %v", err)
		}
		_, err = io.ReadAll(out)
		if err != nil {
			log.Fatalf("💥 failed to read image pull output: %v", err)
		}

		defer out.Close()
		log.Info("⛽ starting seeding process")

		resp, err := cli.ContainerCreate(ctx, &container.Config{
			Image: image,
			Env: []string{
				fmt.Sprintf("TYPESTREAM_CONFIG=%s", config),
			},
		}, &container.HostConfig{NetworkMode: container.NetworkMode("host")}, nil, nil, "ts-seed-container")
		if err != nil {
			log.Fatalf("💥 failed to create container: %v", err)
		}

		id := resp.ID
		if err := cli.ContainerStart(ctx, id, types.ContainerStartOptions{}); err != nil {
			log.Fatalf("💥 failed to start container: %v", err)
		}

		statusCh, errCh := cli.ContainerWait(ctx, id, container.WaitConditionNotRunning)
		select {
		case err := <-errCh:
			if err != nil {
				log.Errorf("💥 failed to wait for container: %v", err)
			}
		case <-statusCh:
		}

		log.Info("🎉 seeding successful")

		log.Info("🗑️  deleting container")
		err = cli.ContainerRemove(context.Background(), id, types.ContainerRemoveOptions{Force: true})
		if err != nil {
			log.Errorf("💥 failed to remove container: %v", err)
		}
		log.Info("✅ done")
	},
}

func init() {
	localCmd.AddCommand(seedCmd)
}
