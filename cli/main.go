package main

import (
	"fmt"
	"io"
	"os"

	"github.com/typestreamio/typestream/cli/cmd"
	"github.com/typestreamio/typestream/cli/pkg/shell"
)

func isPipe() bool {
	stat, err := os.Stdin.Stat()
	if err != nil {
		fmt.Printf("error checking shell pipe: %v", err)
	}
	return (stat.Mode() & os.ModeCharDevice) == 0
}

func main() {
	if isPipe() {
		in, err := io.ReadAll(os.Stdin)
		if err != nil {
			fmt.Printf("unable to read input: %s", err)
			os.Exit(1)
		}

		source := string(in)

		err = shell.Exec(source)
		if err != nil {
			fmt.Printf("error executing shell: %s", err)
			os.Exit(1)
		}
		return
	}

	cmd.Execute()
}
