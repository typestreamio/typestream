package compose

import (
	"bufio"
	_ "embed"
	"os"
	"os/exec"
	"path/filepath"
	"sync"

	"github.com/charmbracelet/log"
	"github.com/typestreamio/typestream/cli/pkg/version"
)

// getComposeDir returns the directory containing compose files
// Walks up from cwd to find docker-compose.yml at project root
func getComposeDir() string {
	cwd, _ := os.Getwd()

	// Walk up directories to find docker-compose.yml
	dir := cwd
	for {
		composePath := filepath.Join(dir, "docker-compose.yml")
		if _, err := os.Stat(composePath); err == nil {
			return dir
		}

		parent := filepath.Dir(dir)
		if parent == dir {
			// Reached filesystem root, not found
			break
		}
		dir = parent
	}

	log.Fatalf("Could not find docker-compose.yml in %s or any parent directory", cwd)
	return ""
}

type Runner struct {
	StdOut     chan string
	composeDir string
	projectEnv []string
}

func NewRunner() *Runner {
	composeDir := getComposeDir()
	return &Runner{
		StdOut:     make(chan string),
		composeDir: composeDir,
		projectEnv: []string{
			"TYPESTREAM_IMAGE=" + version.DockerImage("typestream/server"),
		},
	}
}

func (runner *Runner) Show() string {
	composePath := filepath.Join(runner.composeDir, "docker-compose.yml")
	content, err := os.ReadFile(composePath)
	if err != nil {
		log.Fatalf("Failed to read compose file: %v", err)
	}
	return string(content)
}

func (runner *Runner) RunCommand(arg ...string) error {
	composePath := filepath.Join(runner.composeDir, "docker-compose.yml")

	args := []string{"-p", "typestream", "-f", composePath}
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
