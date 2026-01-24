package version

import (
	"fmt"
	"strings"
)

var (
	Version = "beta"

	CommitHash = "n/a"
)

func BuildVersion() string {
	return fmt.Sprintf("%s %s", Version, CommitHash)
}

func DockerVersion() string {
	return strings.Replace(Version, "+", ".", 1)
}

func DockerImage(imgName string) string {
	image := fmt.Sprintf("%s:%s", imgName, DockerVersion())

	if Version == "beta" {
		// Use GHCR for beta/dev builds
		image = fmt.Sprintf("ghcr.io/typestreamio/%s", image)
	}

	return image
}
