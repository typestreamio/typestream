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

//go:embed compose.yml.tmpl
var composeFile string

type Runner struct {
	StdOut chan string
}

func NewRunner() *Runner {
	return &Runner{
		StdOut: make(chan string),
	}
}

func getProjectRoot() string {
	// Get the directory of the current executable
	execPath, err := os.Executable()
	if err != nil {
		log.Warn("⚠️  Could not determine executable path, using current directory")
		return "."
	}

	// Resolve symlinks
	execPath, err = filepath.EvalSymlinks(execPath)
	if err != nil {
		log.Warn("⚠️  Could not resolve symlinks, using current directory")
		return "."
	}

	// Go up from cli/typestream to project root (2 levels)
	projectRoot := filepath.Dir(filepath.Dir(execPath))
	return projectRoot
}

func (runner *Runner) Show() string {
	buf := bytes.Buffer{}
	tmpl, err := template.New("compose-template").Parse(composeFile)
	if err != nil {
		log.Fatal("💥 failed to parse compose template: %v", err)
	}

	err = tmpl.Execute(&buf, struct {
		Image       string
		ProjectRoot string
	}{
		Image:       version.DockerImage("typestream/server"),
		ProjectRoot: getProjectRoot(),
	})
	if err != nil {
		log.Fatal("💥 failed to execute compose template: %v", err)
	}
	return buf.String()
}

func (runner *Runner) RunCommand(arg ...string) error {
	tmpFile, err := os.CreateTemp("", "docker-compose.*.yml")
	if err != nil {
		log.Fatalf("💥 failed to create temporary file: %v", err)
	}
	defer os.Remove(tmpFile.Name())

	tmpl, err := template.New("compose-template").Parse(composeFile)
	if err != nil {
		log.Fatal("💥 failed to parse compose template: %v", err)
	}

	err = tmpl.Execute(tmpFile, struct {
		Image       string
		ProjectRoot string
	}{
		Image:       version.DockerImage("typestream/server"),
		ProjectRoot: getProjectRoot(),
	})

	if err != nil {
		log.Fatal("💥 failed to execute compose template: %v", err)
	}

	err = tmpFile.Close()
	if err != nil {
		log.Fatalf("💥 failed to close temporary file: %v", err)
	}

	args := append([]string{"-f", tmpFile.Name()}, arg...)
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

