package version

import (
	"fmt"
	"strings"
	"time"
)

var (
	Version = "beta"

	CommitHash = "n/a"
)

func BuildVersion() string {
	return fmt.Sprintf("%s %s (%s)", Version, CommitHash, time.Now().Format(time.DateTime))
}

func DockerVersion() string {
	return strings.Replace(Version, "+", ".", 1)
}
