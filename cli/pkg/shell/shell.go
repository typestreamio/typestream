package shell

import (
	"context"
	"fmt"
	"time"

	"os"
	"os/signal"

	"github.com/chzyer/readline"
	"github.com/typestreamio/typestream/cli/pkg/grpc"
	"github.com/typestreamio/typestream/cli/pkg/interactive_session_service"
	"google.golang.org/grpc/status"
)

type shell struct {
	client    *grpc.Client
	sessionId string
}

// TODO: get user id from config
func newShell(ctx context.Context, userId string) (*shell, error) {
	client := grpc.NewClient()
	startSessionResponse, err := client.StartSession(ctx, &interactive_session_service.StartSessionRequest{UserId: userId})
	if err != nil {
		fmt.Printf("💥 failed to start session: %v\n", err)
		return nil, err
	}

	return &shell{
		client:    client,
		sessionId: startSessionResponse.SessionId,
	}, nil
}

func (s *shell) runProgram(ctx context.Context, source string) (*interactive_session_service.RunProgramResponse, error) {
	return s.client.RunProgram(ctx,
		&interactive_session_service.RunProgramRequest{SessionId: s.sessionId, Source: source})
}

func (s *shell) getProgramOutput(ctx context.Context, id string) (interactive_session_service.InteractiveSessionService_GetProgramOutputClient, error) {
	return s.client.GetProgramOutput(ctx, &interactive_session_service.GetProgramOutputRequest{Id: id, SessionId: s.sessionId})
}

func (s *shell) waitToKillProgram(c chan os.Signal, cancel context.CancelFunc, id string) {
	<-c

	cancel()

	ctx, canc := context.WithTimeout(context.Background(), 10*time.Second)
	defer canc()

	source := fmt.Sprintf("kill %s", id)
	_, err := s.runProgram(ctx, source)
	if err != nil {
		fmt.Printf("💥 failed to kill program: %v\n", err)
	}
}

func (s *shell) close() error {
	return s.client.Close()
}

func Run() {
	c := make(chan os.Signal, 1)
	signal.Notify(c, os.Interrupt)

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Minute)
	defer cancel()

	s, err := newShell(ctx, "42")
	if err != nil {
		return
	}

	defer s.close()

	p := NewPrompt()

	homeDir, err := os.UserHomeDir()
	if err != nil {
		fmt.Printf("💥 failed to get user home dir: %v\n", err)
		os.Exit(1)
	}

	rl, err := readline.NewEx(&readline.Config{
		Prompt:            "> ",
		HistoryFile:       fmt.Sprintf("%s/.typestream_history", homeDir),
		HistorySearchFold: true,
		InterruptPrompt:   "^C",
		EOFPrompt:         "exit",
	})

	if err != nil {
		fmt.Printf("💥 failed to create readline: %v\n", err)
		os.Exit(1)
	}

	for {
		rl.SetPrompt(p.Primary())
		input, err := rl.Readline()
		if err != nil || input == "exit" {
			break
		}
		if len(input) == 0 {
			continue
		}

		runProgramResponse, err := s.runProgram(ctx, input)
		if err != nil {
			if s, ok := status.FromError(err); ok {
				fmt.Println(s.Message())
			} else {
				fmt.Printf("💥 failed to run program: %v\n", err)
			}
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
			go s.waitToKillProgram(c, cancel, runProgramResponse.Id)

			runProgramResponse, err := s.getProgramOutput(ctx, runProgramResponse.Id)
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

	//TODO this can be improved. It may take a while to stop the session so we should stream the output
	//to give the user feedback on the process (right now it's a blocking process)
	stopSessionResponse, err := s.client.StopSession(ctx, &interactive_session_service.StopSessionRequest{SessionId: s.sessionId})
	if err != nil {
		fmt.Printf("💥 failed to stop session: %v\n", err)
		os.Exit(1)
	}

	if stopSessionResponse.StdOut != "" {
		println(stopSessionResponse.StdOut)
	}
	if stopSessionResponse.StdErr != "" {
		println(stopSessionResponse.StdErr)
	}

	fmt.Println("👋 Bye!")
}

func Exec(source string) error {
	c := make(chan os.Signal, 1)
	signal.Notify(c, os.Interrupt)

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Minute)
	defer cancel()

	s, err := newShell(ctx, "42")
	if err != nil {
		return err
	}

	defer s.close()

	runProgramResponse, err := s.runProgram(ctx, source)
	if err != nil {
		if s, ok := status.FromError(err); ok {
			fmt.Println(s.Message())
		} else {
			fmt.Printf("💥 failed to run program: %v\n", err)
		}
		return err
	}

	if runProgramResponse.StdOut != "" {
		println(runProgramResponse.StdOut)
	}
	if runProgramResponse.StdErr != "" {
		println(runProgramResponse.StdErr)
	}

	if runProgramResponse.HasMoreOutput {
		ctx, cancel := context.WithCancel(ctx)
		go s.waitToKillProgram(c, cancel, runProgramResponse.Id)

		runProgramResponse, err := s.getProgramOutput(ctx, runProgramResponse.Id)
		if err != nil {
			fmt.Printf("💥 failed to get program output: %v\n", err)
			return err
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
	return nil
}
