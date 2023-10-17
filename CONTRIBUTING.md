# Contributing

We love every form of contribution. By participating in this project, you agree
to abide to the `TypeStream` [code of conduct](/CODE_OF_CONDUCT.md).

## Prerequisites

- [Gradle](https://gradle.org/)
- [Docker](https://www.docker.com/) and [Docker Compose](https://docs.docker.com/compose/)
- [Minikube](https://minikube.sigs.k8s.io/docs/start/)
- [Go](https://go.dev)
- [Protobuf](https://protobuf.dev/)
- [Make](https://www.gnu.org/software/make/)
- [Redpanda](https://redpanda.com/) (you need `rpk` locally)

## Set up your machine

`TypeStream` is a [Kotlin](https://kotlinlang.org/) [gRPC](https://grpc.io/)
application. We use [Gradle](https://gradle.org/) as our build tool. You can
find the instructions to install it [here](https://gradle.org/install/).

While it's not required, we recommend [IntelliJ
IDEA](https://www.jetbrains.com/idea/) to work with our Kotlin codebase.

We also provide an official CLI application called `typestream`, available at
[/cli](/cli). The CLI is written in [Go](https://go.dev).

Clone `TypeStream` from source:

```sh
$ git clone https://github.com/typestreamio/typestream.git
# Cloning into 'typestream'...
# etc..
$ cd typestream
```

A good way of making sure everything is all right is running the test suite:

```sh
./gradlew check
```

For the CLI, you can run:

```bash
cd cli
make
```

Open an [issue](https://github.com/typestreamio/typestream/issues/new) if you run
into any problem.

## Building and running TypeStream

You can build the `TypeStream` by running:

```sh
./gradlew build
```

If you use IntelliJ IDEA, we provide a run configuration that you can use to run
the server. It uses the script `scripts/dev/kafkastart.sh` to start a local
Redpanda cluster.

Otherwise, you can run the server with:

```sh
./scripts/dev/server.sh
```

and then, from another window terminal, the CLI with:

```sh
./scripts/dev/shell.sh
```

While this is sufficient to run the server and play around with the CLI
features, it doesn't allow you to work with `TypeStream` "cloud native" features
(such as the long running jobs scheduler).

To do so, you need to run `TypeStream` in "k8s mode". The first thing to do is
to push the beta images to a local docker registry. In one terminal, run:

```sh
docker run --rm -it --network=host alpine ash -c "apk add socat && socat TCP-LISTEN:5000,reuseaddr,fork TCP:$(minikube -p typestream ip):5000"
```

so that the docker registry is available at `localhost:5000`. Then, in another terminal, run:

```sh
./scripts/dev/push-images.sh
```

to push the beta images to the local registry. Finally, run:

```sh
cd cli
make
./typestream k8s create # add --redpanda to also start a Redpanda cluster inside k8s
```

to create a local k8s cluster with
[Minikube](https://minikube.sigs.k8s.io/docs/start/) using the `beta` images.

Once you have an healthy minikube cluster, you can connect to the `TypeStream`
server with the CLI as long as you're running this command in the background:

```sh
kubectl port-forward -n typestream svc/server 4242:4242
```

## Testing

We try to cover as much as we can with testing. The goal is having each single
feature covered by one or more tests. Adding more tests is a great way of
contributing to the project!

### Running the tests

Once you are [set up](#set-up-your-machine), you can run the test suite with one
command:

```sh
./gradlew test
```

## Test your change

You can create a branch for your changes and try to build from the source as you
go:

```sh
./gradlew build
```

## Submit a pull request

Push your branch to your `TypeStream` fork and open a pull request against the
main branch. We use [conventional commits](https://www.conventionalcommits.org/)
for our commit messages so follow the convention in the title of your PR
(otherwise, the build will fail).
