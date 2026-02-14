package io.typestream.compiler

import io.typestream.compiler.RuntimeType.KAFKA
import io.typestream.compiler.node.JoinType
import io.typestream.compiler.node.Node
import io.typestream.compiler.node.NodeJoin
import io.typestream.compiler.node.NodeNoOp
import io.typestream.compiler.node.NodeShellSource
import io.typestream.compiler.node.NodeStreamSource
import io.typestream.compiler.types.DataStream
import io.typestream.compiler.types.Encoding
import io.typestream.graph.Graph
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class ProgramTest {
    private fun buildProgram(vararg node: Graph<Node>): Program {
        val root: Graph<Node> = Graph(NodeNoOp("no-op"))
        node.forEach { root.addChild(it) }
        return Program("42", root)
    }

    @Nested
    inner class RuntimeDetection {
        @Test
        fun `raises when detection is not possible`() {
            val program = buildProgram(
                Graph(
                    NodeStreamSource(
                        "cat",
                        DataStream.fromString("/dev/pulsar/local/topics/books", ""),
                        Encoding.AVRO
                    )
                )
            )

            assertThatThrownBy { program.runtime() }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessage("could not detect runtime correctly")
        }

        @Test
        fun `detects kafka runtime name`() {
            val program = buildProgram(
                Graph(
                    NodeStreamSource(
                        "cat",
                        DataStream.fromString("/dev/kafka/local/topics/books", ""),
                        Encoding.AVRO
                    )
                )
            )

            assertThat(program.runtime()).extracting("name", "type").containsExactly("local", KAFKA)
        }

        @Test
        fun `detects multiple runtime`() {
            val program = buildProgram(
                Graph(
                    NodeStreamSource(
                        "cat",
                        DataStream.fromString("/dev/kafka/local/topics/books", ""),
                        Encoding.AVRO
                    )
                ),
                Graph(
                    NodeStreamSource(
                        "cat",
                        DataStream.fromString("/dev/kafka/remote/topics/ratings", ""),
                        Encoding.AVRO
                    )
                )
            )
            assertThatThrownBy { program.runtime() }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessage("multi runtime operation detected: local + remote")
        }

        @Test
        fun `detects runtime in pipe commands`() {
            val cat: Graph<Node> = Graph(
                NodeStreamSource(
                    "cat",
                    DataStream.fromString("/dev/kafka/local/topics/books", ""),
                    Encoding.AVRO
                )
            )

            val join: Graph<Node> = Graph(
                NodeJoin(
                    "join",
                    DataStream.fromString("/dev/kafka/local/topics/ratings", ""),
                    JoinType()
                )
            )

            cat.addChild(join)

            val program = buildProgram(cat)

            assertThat(program.runtime()).extracting("name", "type").containsExactly("local", KAFKA)
        }

        @Test
        fun `detects shell runtime`() {
            val program = buildProgram(
                Graph(NodeShellSource("ls", listOf(DataStream.fromString("/dev/ls", "dev"))))
            )

            assertThat(program.runtime()).extracting("name", "type").containsExactly("shell", RuntimeType.SHELL)
        }

        @Test
        fun `detects shell runtime for empty graphs`() {
            val program = buildProgram(Graph(NodeNoOp("no-op")))

            assertThat(program.runtime()).extracting("name", "type").containsExactly("shell", RuntimeType.SHELL)
        }
    }
}
