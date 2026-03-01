package grpc

import (
	"testing"
)

func TestNewClient_ValidAddress(t *testing.T) {
	client := NewClient("localhost:4242")
	if client == nil {
		t.Fatal("expected non-nil client")
	}
	if client.conn == nil {
		t.Fatal("expected non-nil connection")
	}
	if err := client.Close(); err != nil {
		t.Fatalf("unexpected error closing client: %v", err)
	}
}

func TestNewClient_CustomAddress(t *testing.T) {
	client := NewClient("myserver.example.com:9090")
	if client == nil {
		t.Fatal("expected non-nil client")
	}
	defer func() { _ = client.Close() }()

	target := client.conn.Target()
	if target != "myserver.example.com:9090" {
		t.Fatalf("expected target myserver.example.com:9090, got %s", target)
	}
}
