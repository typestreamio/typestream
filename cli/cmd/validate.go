package cmd

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"time"

	"github.com/spf13/cobra"
	"github.com/typestreamio/typestream/cli/pkg/grpc"
	"github.com/typestreamio/typestream/cli/pkg/pipeline_service"
	"google.golang.org/protobuf/encoding/protojson"

	job_service "github.com/typestreamio/typestream/cli/pkg/job_service"
)

var validateCmd = &cobra.Command{
	Use:   "validate [file]",
	Short: "Validates a pipeline definition file",
	Args:  cobra.ExactArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		content, err := os.ReadFile(args[0])
		if err != nil {
			fmt.Printf("error reading file: %v\n", err)
			os.Exit(1)
		}

		metadata, graph, err := parsePipelineFile(content)
		if err != nil {
			fmt.Printf("error parsing pipeline file: %v\n", err)
			os.Exit(1)
		}

		client := grpc.NewClient()
		ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
		defer cancel()
		defer func() { _ = client.Close() }()

		resp, err := client.ValidatePipeline(ctx, &pipeline_service.ValidatePipelineRequest{
			Metadata: metadata,
			Graph:    graph,
		})
		if err != nil {
			fmt.Printf("error validating pipeline: %v\n", err)
			os.Exit(1)
		}

		if resp.Valid {
			fmt.Printf("pipeline '%s' is valid\n", metadata.Name)
		} else {
			fmt.Printf("pipeline '%s' has errors:\n", metadata.Name)
			for _, e := range resp.Errors {
				fmt.Printf("  - %s\n", e)
			}
			os.Exit(1)
		}
	},
}

func init() {
	rootCmd.AddCommand(validateCmd)
}

// parsePipelineFile parses a .typestream.json file into metadata and graph.
func parsePipelineFile(content []byte) (*pipeline_service.PipelineMetadata, *job_service.PipelineGraph, error) {
	var raw map[string]json.RawMessage
	if err := json.Unmarshal(content, &raw); err != nil {
		return nil, nil, fmt.Errorf("invalid JSON: %w", err)
	}

	var name string
	if nameRaw, ok := raw["name"]; ok {
		if err := json.Unmarshal(nameRaw, &name); err != nil {
			return nil, nil, fmt.Errorf("invalid 'name' field: %w", err)
		}
	} else {
		return nil, nil, fmt.Errorf("missing 'name' field")
	}

	version := "1"
	if versionRaw, ok := raw["version"]; ok {
		if err := json.Unmarshal(versionRaw, &version); err != nil {
			return nil, nil, fmt.Errorf("invalid 'version' field: %w", err)
		}
	}

	var description string
	if descRaw, ok := raw["description"]; ok {
		if err := json.Unmarshal(descRaw, &description); err != nil {
			return nil, nil, fmt.Errorf("invalid 'description' field: %w", err)
		}
	}

	graphRaw, ok := raw["graph"]
	if !ok {
		return nil, nil, fmt.Errorf("missing 'graph' field")
	}

	graph := &job_service.PipelineGraph{}
	if err := protojson.Unmarshal(graphRaw, graph); err != nil {
		return nil, nil, fmt.Errorf("invalid 'graph': %w", err)
	}

	metadata := &pipeline_service.PipelineMetadata{
		Name:        name,
		Version:     version,
		Description: description,
	}

	return metadata, graph, nil
}
