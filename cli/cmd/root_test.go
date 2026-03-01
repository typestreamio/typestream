package cmd

import (
	"os"
	"testing"
)

func TestResolveServerAddress_Flag(t *testing.T) {
	addr, err := resolveServerAddress("myhost:4242")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if addr != "myhost:4242" {
		t.Fatalf("expected myhost:4242, got %s", addr)
	}
}

func TestResolveServerAddress_EnvVar(t *testing.T) {
	t.Setenv("TYPESTREAM_SERVER", "envhost:1234")

	addr, err := resolveServerAddress("")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if addr != "envhost:1234" {
		t.Fatalf("expected envhost:1234, got %s", addr)
	}
}

func TestResolveServerAddress_FlagTakesPrecedence(t *testing.T) {
	t.Setenv("TYPESTREAM_SERVER", "envhost:1234")

	addr, err := resolveServerAddress("flaghost:5678")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if addr != "flaghost:5678" {
		t.Fatalf("expected flaghost:5678, got %s", addr)
	}
}

func TestResolveServerAddress_Missing(t *testing.T) {
	os.Unsetenv("TYPESTREAM_SERVER")

	_, err := resolveServerAddress("")
	if err == nil {
		t.Fatal("expected error when no server address is provided")
	}
}

func TestServerFlagRegistered(t *testing.T) {
	f := rootCmd.PersistentFlags().Lookup("server")
	if f == nil {
		t.Fatal("expected --server persistent flag to be registered")
	}
	if f.Usage == "" {
		t.Fatal("expected --server flag to have usage text")
	}
}

func TestServerFlagParsed(t *testing.T) {
	// Reset the flag for this test
	oldVal := serverFlag
	defer func() { serverFlag = oldVal }()

	rootCmd.SetArgs([]string{"--server", "parsed:9999", "--help"})
	// Execute with --help so it doesn't actually try to connect
	_ = rootCmd.Execute()

	if serverFlag != "parsed:9999" {
		t.Fatalf("expected serverFlag to be parsed:9999, got %s", serverFlag)
	}
}
