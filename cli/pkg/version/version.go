package version

import (
	"fmt"
	"time"
)

var (
	Version = "beta"

	CommitHash = "n/a"
)

func BuildVersion() string {
	return fmt.Sprintf("%s %s (%s)", Version, CommitHash, time.Now().Format(time.DateTime))
}
