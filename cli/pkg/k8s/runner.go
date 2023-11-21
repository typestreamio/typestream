package k8s

import (
	"bytes"
	_ "embed"
	"fmt"
	"html/template"
	"os"
	"os/exec"

	"github.com/charmbracelet/log"
	"github.com/typestreamio/typestream/cli/pkg/version"
)

//go:embed typestream.yaml.tmpl
var typestreamTmpl string

//go:embed redpanda.yaml
var redpandaTmpl string

//go:embed seeder.yaml.tmpl
var seederTmpl string

type templateData struct {
	Image     string
	Namespace string
}

func parseTemplate(text string, data templateData) string {
	buf := bytes.Buffer{}
	tmpl, err := template.New(fmt.Sprintf("%s-template", data.Image)).Parse(text)
	if err != nil {
		log.Fatal("💥 failed to parse typestream resources template: ", err)
	}

	err = tmpl.Execute(&buf, data)
	if err != nil {
		log.Fatal("💥 failed to execute typestream resources template: ", err)
	}

	return buf.String()

}

type Runner struct {
	namespace string
}

func NewRunner(namespace string) *Runner {
	return &Runner{namespace: namespace}
}

func (r *Runner) apply(tmpl string) error {
	cmd := exec.Command("kubectl", "apply", "-f", "-")
	cmd.Stdin = bytes.NewBufferString(tmpl)
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	return cmd.Run()
}

func (r *Runner) Apply(redpanda bool) error {
	serverTmpl := r.Show()

	if redpanda {
		return r.apply(serverTmpl + "\n\n---\n\n" + redpandaTmpl)
	}

	return r.apply(serverTmpl)
}

func (r *Runner) ApplySeeder() error {
	seederTmpl := parseTemplate(seederTmpl, templateData{
		Image:     version.DockerImage("typestream/tools-seeder"),
		Namespace: r.namespace,
	})
	return r.apply(seederTmpl)
}

func (r *Runner) Show() string {
	return parseTemplate(typestreamTmpl, templateData{
		Image:     version.DockerImage("typestream/server"),
		Namespace: r.namespace,
	})
}
