BINARY  := typestream
REPO    := github.com/typestreamio/typestream/cli
HASH    := $(shell git rev-parse --short HEAD)
TAG     := $(shell git describe --tags --always --abbrev=0 2> /dev/null)
VERSION := $(shell echo "${TAG}")

LDFLAGS := -ldflags="-X '${REPO}/pkg/version.Version=${VERSION}' -X '${REPO}/pkg/version.CommitHash=${HASH}'"

build:
	go build ${LDFLAGS} -o ${BINARY}

proto:
	@protoc --go_out=pkg/program_service --go_opt=paths=source_relative --go-grpc_out=pkg/program_service --go-grpc_opt=paths=source_relative --proto_path ../protos/src/main/proto program.proto