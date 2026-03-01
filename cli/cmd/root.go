package cmd

import (
	"fmt"
	"io"
	"os"

	"github.com/spf13/cobra"
	"github.com/typestreamio/typestream/cli/cmd/k8s"
	"github.com/typestreamio/typestream/cli/cmd/local"
	"github.com/typestreamio/typestream/cli/pkg/shell"
	"github.com/typestreamio/typestream/cli/pkg/version"
)

var serverFlag string

var rootCmd = &cobra.Command{
	Use:   "typestream",
	Short: "TypeStream is an abstraction layer for streaming data",
	Long:  `TypeStream allows you to write typed streaming pipeline with a familiar syntax.`,
	Run: func(cmd *cobra.Command, args []string) {
		server := ServerAddress()

		if isPipe() {
			in, err := io.ReadAll(os.Stdin)
			if err != nil {
				fmt.Printf("unable to read input: %s", err)
				os.Exit(1)
			}
			err = shell.Exec(server, string(in))
			if err != nil {
				fmt.Printf("error executing shell: %s", err)
				os.Exit(1)
			}
			return
		}

		shell.Run(server)
	},
}

func isPipe() bool {
	stat, err := os.Stdin.Stat()
	if err != nil {
		fmt.Printf("error checking shell pipe: %v", err)
	}
	return (stat.Mode() & os.ModeCharDevice) == 0
}

// resolveServerAddress returns the server address from the flag or env var, or an error.
func resolveServerAddress(flag string) (string, error) {
	if flag != "" {
		return flag, nil
	}
	if env := os.Getenv("TYPESTREAM_SERVER"); env != "" {
		return env, nil
	}
	return "", fmt.Errorf("server address is required (use --server flag or TYPESTREAM_SERVER env var)")
}

// ServerAddress returns the server address from the --server flag or TYPESTREAM_SERVER env var.
func ServerAddress() string {
	addr, err := resolveServerAddress(serverFlag)
	if err != nil {
		fmt.Printf("error: %s\n", err)
		os.Exit(1)
	}
	return addr
}

func Execute() {
	err := rootCmd.Execute()
	if err != nil {
		os.Exit(1)
	}
}

func init() {
	rootCmd.PersistentFlags().StringVar(&serverFlag, "server", "", "TypeStream server address (e.g. localhost:4242)")
	rootCmd.AddCommand(k8s.NewCommand())
	rootCmd.AddCommand(local.NewCommand())
	rootCmd.Version = version.BuildVersion()
}
