# gRPC Server

A reference of the gRPC services `TypeStream` exposes.

## RunProgram

The `RunProgram` service is responsible for making sense of clients requests.

As they come in form of shell scripts (or commands, the language doesn't really
make a distinction), the service returns a structured program back to the
clients.

The server compiles clients requests and returns a program back to the client.

See [compiler](/concepts/components.md/#compiler) for more information about this process.

## GetJobOutput

Most programs are long-running data streaming jobs. For that reason, the server
exposes a service that lets clients stream the output of a running program so
end-users can check out their pipelines.
