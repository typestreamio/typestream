BINARY  := typestream

build:
	go build -o ${BINARY}

proto:
	@protoc --go_out=pkg/interactive_session_service --go_opt=paths=source_relative --go-grpc_out=pkg/interactive_session_service --go-grpc_opt=paths=source_relative --proto_path ../protos/src/main/proto interactive_session.proto
	@protoc --go_out=pkg/job_service --go_opt=paths=source_relative --go-grpc_out=pkg/job_service --go-grpc_opt=paths=source_relative --proto_path ../protos/src/main/proto job.proto
	@protoc --go_out=pkg/filesystem_service --go_opt=paths=source_relative --go-grpc_out=pkg/filesystem_service --go-grpc_opt=paths=source_relative --proto_path ../protos/src/main/proto filesystem.proto
	@protoc --go_out=pkg/pipeline_service --go_opt=paths=source_relative --go-grpc_out=pkg/pipeline_service --go-grpc_opt=paths=source_relative --proto_path ../protos/src/main/proto pipeline.proto
