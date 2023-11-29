package io.typestream.konfig

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.FileInputStream

internal class KonfigSourceTest {

    @KonfigSource(prefix = "server")
    data class ServerConfig(val host: String, val port: Int, val protocol: String?)

    @KonfigSource(prefix = "db")
    data class DbConfig(val endpoint: String)

    @KonfigSource
    data class Config(val serverHost: String, val serverPort: Int)

    @KonfigSource
    data class NestedConfig(val db: DbConfig, val server: ServerConfig)

    @KonfigSource(prefix = "nested")
    data class NestedWithPrefix(val db: DbConfig, val server: ServerConfig)

    @Test
    fun `loads a simple config`() {
        class App(konfig: Konfig) {
            val serverConfig by konfig.inject<ServerConfig>()
        }

        val konfig = Konfig(FileInputStream("src/test/resources/application.properties"))
        val app = App(konfig)

        assertThat(app.serverConfig).extracting("host", "port").containsExactly("localhost", 4242)
    }

    @Test
    fun `loads a simple config without prefix`() {
        class App(konfig: Konfig) {
            val config by konfig.inject<Config>()
        }

        val konfig = Konfig(FileInputStream("src/test/resources/application.properties"))
        val app = App(konfig)

        assertThat(app.config).extracting("serverHost", "serverPort").containsExactly("localhost", 4242)
    }

    @Nested
    inner class NestedTest {
        @Test
        fun `loads a nested config`() {
            class App(konfig: Konfig) {
                val config by konfig.inject<NestedConfig>()
            }

            val konfig = Konfig(FileInputStream("src/test/resources/application.properties"))
            val app = App(konfig)

            assertThat(app.config)
                .extracting("db.endpoint", "server.host", "server.port")
                .containsExactly("http://db.local:5432", "localhost", 4242)
        }

        @Test
        fun `loads a nested config with prefix`() {
            class App(konfig: Konfig) {
                val config by konfig.inject<NestedWithPrefix>()
            }

            val konfig = Konfig(FileInputStream("src/test/resources/nested-prefix.properties"))
            val app = App(konfig)

            assertThat(app.config)
                .extracting("db.endpoint", "server.host", "server.port")
                .containsExactly("http://db.local:5432", "localhost", 4242)
        }
    }

    @Test
    fun `ignores comments`() {
        class App(konfig: Konfig) {
            val config by konfig.inject<ServerConfig>()
        }

        val konfig = Konfig(FileInputStream("src/test/resources/comments.properties"))
        val app = App(konfig)

        assertThat(app.config).extracting("host", "port").containsExactly("localhost", 4242)
    }

}
