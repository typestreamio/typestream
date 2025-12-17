package compose

import (
	"bufio"
	"bytes"
	_ "embed"
	"html/template"
	"os"
	"os/exec"
	"path/filepath"
	"sync"

	"github.com/charmbracelet/log"
	"github.com/typestreamio/typestream/cli/pkg/version"
)

// devProjectName is the Docker Compose project name for dev mode
// Using a separate name avoids conflicts with production "typestream" project
const devProjectName = "typestream-dev"

// getProjectRoot returns the absolute path to the typestream project root
func getProjectRoot() string {
	// Get the executable path and navigate to project root
	exe, err := os.Executable()
	if err != nil {
		// Fallback to current working directory
		cwd, _ := os.Getwd()
		return cwd
	}
	// The CLI binary is at <project>/cli/typestream, so go up one level
	return filepath.Dir(filepath.Dir(exe))
}

// getDevComposeFilePath returns a fixed path for the dev compose file
// Using a fixed path instead of temp files ensures Docker Compose can track state properly
func getDevComposeFilePath() string {
	homeDir, err := os.UserHomeDir()
	if err != nil {
		homeDir = "/tmp"
	}
	typestreamDir := filepath.Join(homeDir, ".typestream")
	if err := os.MkdirAll(typestreamDir, 0755); err != nil {
		log.Warn("Could not create ~/.typestream, using /tmp", "error", err)
		return "/tmp/typestream-compose-deps.yml"
	}
	return filepath.Join(typestreamDir, "compose-deps.yml")
}

//go:embed compose-deps.yml.tmpl
var composeDepsFile string

type DevRunner struct {
	StdOut chan string
}

func NewDevRunner() *DevRunner {
	return &DevRunner{
		StdOut: make(chan string),
	}
}

func (runner *DevRunner) Show() string {
	buf := bytes.Buffer{}
	tmpl, err := template.New("compose-deps-template").Parse(composeDepsFile)
	if err != nil {
		log.Fatal("💥 failed to parse compose-deps template: %v", err)
	}

	err = tmpl.Execute(&buf, struct {
		Image       string
		ProjectRoot string
	}{
		Image:       version.DockerImage("typestream/server"),
		ProjectRoot: getProjectRoot(),
	})
	if err != nil {
		log.Fatal("💥 failed to execute compose-deps template: %v", err)
	}
	return buf.String()
}

func (runner *DevRunner) RunCommand(arg ...string) error {
	composeFilePath := getDevComposeFilePath()

	// Write the compose file to the fixed location
	composeFile, err := os.Create(composeFilePath)
	if err != nil {
		log.Fatalf("💥 failed to create compose file at %s: %v", composeFilePath, err)
	}

	tmpl, err := template.New("compose-deps-template").Parse(composeDepsFile)
	if err != nil {
		log.Fatal("💥 failed to parse compose-deps template: %v", err)
	}

	err = tmpl.Execute(composeFile, struct {
		Image       string
		ProjectRoot string
	}{
		Image:       version.DockerImage("typestream/server"),
		ProjectRoot: getProjectRoot(),
	})

	if err != nil {
		log.Fatal("💥 failed to execute compose-deps template: %v", err)
	}

	err = composeFile.Close()
	if err != nil {
		log.Fatalf("💥 failed to close compose file: %v", err)
	}

	// Use fixed project name and file path to ensure Docker Compose can track state
	args := append([]string{"-p", devProjectName, "-f", composeFilePath}, arg...)
	cmd := exec.Command("docker-compose", args...)

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
