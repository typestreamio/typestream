package shell

import "fmt"

type Prompt struct {
	Env map[string]string
}

func NewPrompt() *Prompt {
	return &Prompt{
		Env: make(map[string]string),
	}
}

func (p *Prompt) Primary() string {
	pwd, ok := p.Env["PWD"]
	if !ok {
		pwd = "/"
	}
	return fmt.Sprintf("%s > ", pwd)
}

func (p *Prompt) setEnv(key, value string) {
	p.Env[key] = value
}

func (p *Prompt) EnvString() string {
	return fmt.Sprintf("%s", p.Env)
}
