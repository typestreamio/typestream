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
func getComposeDir() string {
	exe, err := os.Executable()
	if err != nil {
		log.Fatalf("Failed to get executable path: %v", err)
	}

	// Resolve symlinks to get the real path
	exe, err = filepath.EvalSymlinks(exe)
	if err != nil {
		log.Fatalf("Failed to resolve symlinks: %v", err)
	}

	// The CLI binary is at <project>/cli/typestream (or similar)
	// Compose files are at <project>/cli/pkg/compose/
	cliDir := filepath.Dir(exe)
	composeDir := filepath.Join(cliDir, "pkg", "compose")

	// Check if the compose directory exists
	if _, err := os.Stat(composeDir); os.IsNotExist(err) {
		// Fallback: try relative to current working directory
		cwd, _ := os.Getwd()
		composeDir = filepath.Join(cwd, "cli", "pkg", "compose")
	}

	return composeDir
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
	composePath := filepath.Join(runner.composeDir, "compose.yml")
	content, err := os.ReadFile(composePath)
	if err != nil {
		log.Fatalf("Failed to read compose file: %v", err)
	}
	return string(content)
}

func (runner *Runner) RunCommand(arg ...string) error {
	composePath := filepath.Join(runner.composeDir, "compose.yml")

	args := []string{"-p", "typestream", "-f", composePath}
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
