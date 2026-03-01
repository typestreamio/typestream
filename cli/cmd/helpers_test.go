package cmd

import (
	"bytes"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/typestreamio/typestream/cli/pkg/pipeline_service"
)

// --- resolvePipelineFiles ---

func TestResolvePipelineFiles_SingleFile(t *testing.T) {
	tmp := t.TempDir()
	f := filepath.Join(tmp, "test.typestream.json")
	if err := os.WriteFile(f, []byte("{}"), 0644); err != nil {
		t.Fatal(err)
	}

	files, err := resolvePipelineFiles(f)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(files) != 1 || files[0] != f {
		t.Fatalf("expected [%s], got %v", f, files)
	}
}

func TestResolvePipelineFiles_Directory(t *testing.T) {
	tmp := t.TempDir()
	names := []string{"a.typestream.json", "b.typestream.json"}
	for _, n := range names {
		if err := os.WriteFile(filepath.Join(tmp, n), []byte("{}"), 0644); err != nil {
			t.Fatal(err)
		}
	}
	// non-matching file should be ignored
	if err := os.WriteFile(filepath.Join(tmp, "readme.md"), []byte("hi"), 0644); err != nil {
		t.Fatal(err)
	}

	files, err := resolvePipelineFiles(tmp)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(files) != 2 {
		t.Fatalf("expected 2 files, got %d: %v", len(files), files)
	}
}

func TestResolvePipelineFiles_DirectoryNoMatches(t *testing.T) {
	tmp := t.TempDir()
	if err := os.WriteFile(filepath.Join(tmp, "readme.md"), []byte("hi"), 0644); err != nil {
		t.Fatal(err)
	}

	_, err := resolvePipelineFiles(tmp)
	if err == nil {
		t.Fatal("expected error for directory with no matching files")
	}
	if !strings.Contains(err.Error(), "no .typestream.json files found") {
		t.Fatalf("unexpected error message: %v", err)
	}
}

func TestResolvePipelineFiles_NonExistent(t *testing.T) {
	_, err := resolvePipelineFiles("/nonexistent/path")
	if err == nil {
		t.Fatal("expected error for non-existent path")
	}
}

// --- loadPipelinePlans ---

func TestLoadPipelinePlans_Valid(t *testing.T) {
	tmp := t.TempDir()
	content := `{
		"name": "test-pipeline",
		"version": "2",
		"description": "a test",
		"graph": {"nodes": [], "edges": []}
	}`
	f := filepath.Join(tmp, "test.typestream.json")
	if err := os.WriteFile(f, []byte(content), 0644); err != nil {
		t.Fatal(err)
	}

	plans, err := loadPipelinePlans([]string{f})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(plans) != 1 {
		t.Fatalf("expected 1 plan, got %d", len(plans))
	}
	if plans[0].Metadata.Name != "test-pipeline" {
		t.Fatalf("expected name 'test-pipeline', got '%s'", plans[0].Metadata.Name)
	}
	if plans[0].Metadata.Version != "2" {
		t.Fatalf("expected version '2', got '%s'", plans[0].Metadata.Version)
	}
}

func TestLoadPipelinePlans_InvalidJSON(t *testing.T) {
	tmp := t.TempDir()
	f := filepath.Join(tmp, "bad.typestream.json")
	if err := os.WriteFile(f, []byte("not json"), 0644); err != nil {
		t.Fatal(err)
	}

	_, err := loadPipelinePlans([]string{f})
	if err == nil {
		t.Fatal("expected error for invalid JSON")
	}
}

// --- printPlanTable ---

func TestPrintPlanTable_FilterDeletesTrue(t *testing.T) {
	results := []*pipeline_service.PipelinePlanResult{
		{Name: "pipeline-a", Action: pipeline_service.PipelineAction_CREATE, NewVersion: "1"},
		{Name: "pipeline-b", Action: pipeline_service.PipelineAction_DELETE, CurrentVersion: "1"},
		{Name: "pipeline-c", Action: pipeline_service.PipelineAction_NO_CHANGE, CurrentVersion: "1", NewVersion: "1"},
	}

	var buf bytes.Buffer
	printPlanTable(&buf, results, true)
	output := buf.String()

	if !strings.Contains(output, "pipeline-a") {
		t.Error("expected CREATE result to be shown")
	}
	if strings.Contains(output, "pipeline-b") {
		t.Error("expected DELETE result to be hidden with filterDeletes=true")
	}
	if !strings.Contains(output, "pipeline-c") {
		t.Error("expected NO_CHANGE result to be shown")
	}
}

func TestPrintPlanTable_FilterDeletesFalse(t *testing.T) {
	results := []*pipeline_service.PipelinePlanResult{
		{Name: "pipeline-a", Action: pipeline_service.PipelineAction_CREATE, NewVersion: "1"},
		{Name: "pipeline-b", Action: pipeline_service.PipelineAction_DELETE, CurrentVersion: "1"},
		{Name: "pipeline-c", Action: pipeline_service.PipelineAction_UPDATE, CurrentVersion: "1", NewVersion: "2"},
	}

	var buf bytes.Buffer
	printPlanTable(&buf, results, false)
	output := buf.String()

	if !strings.Contains(output, "pipeline-a") {
		t.Error("expected CREATE result to be shown")
	}
	if !strings.Contains(output, "pipeline-b") {
		t.Error("expected DELETE result to be shown with filterDeletes=false")
	}
	if !strings.Contains(output, "pipeline-c") {
		t.Error("expected UPDATE result to be shown")
	}
}

// --- confirmApply ---

func TestConfirmApply_Yes(t *testing.T) {
	for _, input := range []string{"y\n", "Y\n", "yes\n", "YES\n", "Yes\n"} {
		if !confirmApply(strings.NewReader(input)) {
			t.Errorf("expected true for input %q", input)
		}
	}
}

func TestConfirmApply_No(t *testing.T) {
	for _, input := range []string{"n\n", "N\n", "no\n", "\n", "maybe\n"} {
		if confirmApply(strings.NewReader(input)) {
			t.Errorf("expected false for input %q", input)
		}
	}
}

func TestConfirmApply_EmptyReader(t *testing.T) {
	if confirmApply(strings.NewReader("")) {
		t.Error("expected false for empty reader")
	}
}
