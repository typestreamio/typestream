package shell

import (
	"context"
	"fmt"
	"strings"
	"time"

	"os"
	"os/signal"

	"github.com/alecthomas/chroma/quick"
	"github.com/charmbracelet/log"
	"github.com/reeflective/readline"
	"github.com/typestreamio/typestream/cli/pkg/grpc"
	"github.com/typestreamio/typestream/cli/pkg/program_service"
)

type shell struct {
	client *grpc.Client
}

func newShell() *shell {
	return &shell{
		client: grpc.NewClient(),
	}
}

func (s *shell) close() {
	s.client.Close()
}

func (s *shell) highlighter(line []rune) string {
	var highlighted strings.Builder

	err := quick.Highlight(&highlighted, string(line), "bash", "terminal16m", "xcode")
	if err != nil {
		return string(line)
	}

	return highlighted.String()
}

func (s *shell) completer(line []rune, cursor int) readline.Completions {
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Minute)
	defer cancel()

	completeProgramResponse, err := s.client.CompleteProgram(ctx, &program_service.CompleteProgramRequest{Source: string(line)})
	if err != nil {
		return readline.CompleteValues()
	}
	return readline.CompleteValues(completeProgramResponse.Value...)
}

func Run() {
	c := make(chan os.Signal, 1)
	signal.Notify(c, os.Interrupt)

	s := newShell()

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Minute)
	defer cancel()

	defer s.close()

	if err := s.client.CheckConn(ctx); err != nil {
		log.Fatalf("💥 cannot connect to TypeStream server %v", err)
	}

	p := NewPrompt()

	rl := readline.NewShell()

	rl.SyntaxHighlighter = s.highlighter

	rl.Completer = s.completer

	rl.Prompt.Primary(p.Primary)

	for {
		input, err := rl.Readline()
		if err != nil || input == "exit" {
			break
		}
		if len(input) == 0 {
			continue
		}

		runProgramResponse, err := s.client.RunProgram(ctx, &program_service.RunProgramRequest{Source: input})
		if err != nil {
			fmt.Printf("💥 failed to run program: %v\n", err)
			continue
		}

		if runProgramResponse.StdOut != "" {
			println(runProgramResponse.StdOut)
		}
		if runProgramResponse.StdErr != "" {
			println(runProgramResponse.StdErr)
		}

		p.setEnv("PWD", runProgramResponse.Env["PWD"])

		if runProgramResponse.HasMoreOutput {
			ctx, cancel := context.WithCancel(ctx)
			go func() {
				<-c
				cancel()

				ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
				defer cancel()

				source := fmt.Sprintf("kill %s", runProgramResponse.Id)
				_, err := s.client.RunProgram(ctx, &program_service.RunProgramRequest{Source: source})
				if err != nil {
					fmt.Printf("💥 failed to kill program: %v\n", err)
				}
			}()

			runProgramResponse, err := s.client.GetProgramOutput(ctx, &program_service.GetProgramOutputRequest{Id: runProgramResponse.Id})
			if err != nil {
				fmt.Printf("💥 failed to get program output: %v\n", err)
				break
			}

			for {
				line, err := runProgramResponse.Recv()
				if err != nil {
					if ctx.Err() != context.Canceled {
						fmt.Println("Error: ", err)
					}
					break
				}
				fmt.Println(line.StdOut)
			}
		}
	}
	fmt.Println("👋 Bye!")
}
