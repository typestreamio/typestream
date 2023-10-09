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

//go:embed typestream.yaml
var typestreamTmpl string

//go:embed redpanda.yaml
var redpandaTmpl string

//go:embed seeder.yaml
var seederTmpl string

func parseTemplate(text string, imgName string) string {
	buf := bytes.Buffer{}
	tmpl, err := template.New(fmt.Sprintf("%s-template", imgName)).Parse(text)
	if err != nil {
		log.Fatal("💥 failed to parse typestream resources template: ", err)
	}

	image := fmt.Sprintf("%s:%s", imgName, version.DockerVersion())

	version := version.Version
	if version == "beta" {
		image = fmt.Sprintf("localhost:5000/%s", image)
	}

	err = tmpl.Execute(&buf, struct{ Image string }{Image: image})
	if err != nil {
		log.Fatal("💥 failed to execute typestream resources template: ", err)
	}

	return buf.String()

}

type Runner struct {
}

func NewRunner() *Runner {
	return &Runner{}
}

func (r *Runner) apply(tmpl string) error {
	cmd := exec.Command("kubectl", "apply", "-f", "-")
	cmd.Stdin = bytes.NewBufferString(tmpl)
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	return cmd.Run()
}

func (r *Runner) Apply(redpanda bool) error {
	if redpanda {
		return r.apply(parseTemplate(typestreamTmpl, "typestream/server") + "\n\n---\n\n" + redpandaTmpl)
	}

	return r.apply(parseTemplate(typestreamTmpl, "typestream/server"))
}

func (r *Runner) ApplySeeder() error {
	return r.apply(parseTemplate(seederTmpl, "typestream/tools-seeder"))
}
