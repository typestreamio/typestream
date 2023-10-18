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
		image = fmt.Sprintf("localhost:5000/%s", image)
	}

	return image
}
