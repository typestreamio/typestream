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
func getProjectRoot() string {
	composeDir := getComposeDir()
	// composeDir is <project>/cli/pkg/compose, so go up 3 levels
	return filepath.Dir(filepath.Dir(filepath.Dir(composeDir)))
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
		"-p", "typestream-dev",
		"-f", composePath,
		"-f", composeDevPath,
	}
	args = append(args, arg...)

	cmd := exec.Command("docker-compose", args...)
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
