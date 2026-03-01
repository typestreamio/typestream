package cmd

import (
	"bufio"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"text/tabwriter"

	"github.com/typestreamio/typestream/cli/pkg/pipeline_service"
)

// resolvePipelineFiles returns the list of .typestream.json files for the given path.
// If path is a directory, it scans for *.typestream.json files.
// If path is a file, it returns that file.
func resolvePipelineFiles(path string) ([]string, error) {
	info, err := os.Stat(path)
	if err != nil {
		return nil, fmt.Errorf("error reading path: %w", err)
	}

	if !info.IsDir() {
		return []string{path}, nil
	}

	entries, err := os.ReadDir(path)
	if err != nil {
		return nil, fmt.Errorf("error reading directory: %w", err)
	}

	var files []string
	for _, entry := range entries {
		if !entry.IsDir() && strings.HasSuffix(entry.Name(), ".typestream.json") {
			files = append(files, filepath.Join(path, entry.Name()))
		}
	}

	if len(files) == 0 {
		return nil, fmt.Errorf("no .typestream.json files found in directory")
	}

	return files, nil
}

// loadPipelinePlans reads and parses each file into a PipelinePlan.
func loadPipelinePlans(files []string) ([]*pipeline_service.PipelinePlan, error) {
	var plans []*pipeline_service.PipelinePlan
	for _, f := range files {
		content, err := os.ReadFile(f)
		if err != nil {
			return nil, fmt.Errorf("error reading file %s: %w", f, err)
		}

		metadata, graph, err := parsePipelineFile(content)
		if err != nil {
			return nil, fmt.Errorf("error parsing %s: %w", f, err)
		}

		plans = append(plans, &pipeline_service.PipelinePlan{
			Metadata: metadata,
			Graph:    graph,
		})
	}
	return plans, nil
}

// printPlanTable renders a plan results table to the given writer.
// When filterDeletes is true, DELETE results are hidden (single-file additive mode).
func printPlanTable(w io.Writer, results []*pipeline_service.PipelinePlanResult, filterDeletes bool) {
	tw := tabwriter.NewWriter(w, 0, 0, 2, ' ', 0)
	_, _ = fmt.Fprintln(tw, "NAME\tACTION\tCURRENT VERSION\tNEW VERSION")
	for _, r := range results {
		if filterDeletes && r.Action == pipeline_service.PipelineAction_DELETE {
			continue
		}
		action := formatAction(r.Action)
		_, _ = fmt.Fprintf(tw, "%s\t%s\t%s\t%s\n",
			r.Name,
			action,
			r.CurrentVersion,
			r.NewVersion,
		)
	}
	_ = tw.Flush()
}

func formatAction(action pipeline_service.PipelineAction) string {
	switch action {
	case pipeline_service.PipelineAction_CREATE:
		return "\033[32m+ create\033[0m"
	case pipeline_service.PipelineAction_UPDATE:
		return "\033[33m~ update\033[0m"
	case pipeline_service.PipelineAction_NO_CHANGE:
		return "\033[2m  unchanged\033[0m"
	case pipeline_service.PipelineAction_DELETE:
		return "\033[31m- delete\033[0m"
	default:
		return "  unknown"
	}
}

// confirmApply prompts the user for confirmation and returns true if they accept.
func confirmApply(in io.Reader) bool {
	fmt.Print("Apply these changes? [y/N]: ")
	scanner := bufio.NewScanner(in)
	if !scanner.Scan() {
		return false
	}
	answer := strings.TrimSpace(strings.ToLower(scanner.Text()))
	return answer == "y" || answer == "yes"
}
