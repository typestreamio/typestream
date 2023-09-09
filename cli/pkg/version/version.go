package version

import (
	"fmt"
	"strings"
)

const (
	Version = "beta"

	CommitHash = "n/a"
)

func BuildVersion() string {
	return fmt.Sprintf("%s %s", Version, CommitHash)
}

func DockerVersion() string {
	return strings.Replace(Version, "+", ".", 1)
}
