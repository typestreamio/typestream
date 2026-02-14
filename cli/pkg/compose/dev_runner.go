package compose

import (
	"bufio"
	"os"
	"os/exec"
	"path/filepath"
	"sync"

	"github.com/charmbracelet/log"
)

// DevRunner handles docker-compose operations for development mode
// Uses docker-compose.yml + docker-compose.dev.yml overlay (server runs on host)
type DevRunner struct {
	StdOut     chan string
	composeDir string
	projectEnv []string
}

// getProjectRoot returns the absolute path to the typestream project root
// This is the same as getComposeDir() since docker-compose.yml lives at the project root
func getProjectRoot() string {
	return getComposeDir()
}

func NewDevRunner() *DevRunner {
	composeDir := getComposeDir()
	projectRoot := getProjectRoot()

	return &DevRunner{
		StdOut:     make(chan string),
		composeDir: composeDir,
		projectEnv: []string{
			"TYPESTREAM_PROJECT_ROOT=" + projectRoot,
		},
	}
}

func (runner *DevRunner) RunCommand(arg ...string) error {
	composePath := filepath.Join(runner.composeDir, "docker-compose.yml")
	composeDevPath := filepath.Join(runner.composeDir, "docker-compose.dev.yml")

	// Use both base compose and dev overlay
	args := []string{
		"-p", "typestream",
		"-f", composePath,
		"-f", composeDevPath,
	}
	args = append(args, arg...)

	cmd := exec.Command("docker", append([]string{"compose"}, args...)...)
	cmd.Dir = runner.composeDir
	cmd.Env = append(os.Environ(), runner.projectEnv...)

	stdErr, err := cmd.StderrPipe()
	if err != nil {
		log.Fatalf("Failed to get stderr pipe: %v", err)
	}

	err = cmd.Start()
	if err != nil {
		log.Fatalf("Failed to start docker compose: %v", err)
	}

	wg := &sync.WaitGroup{}
	wg.Add(1)
	go func() {
		defer wg.Done()
		scanner := bufio.NewScanner(stdErr)
		for scanner.Scan() {
			m := scanner.Text()
			runner.StdOut <- m
		}
	}()

	wg.Wait()

	return cmd.Wait()
}
