package io.typestream.konfig

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.FileInputStream

internal class KonfigSourceMapTest {

    @KonfigSource
    data class ServerConfig(val host: String, val port: Int)

    @KonfigSource("servers")
    data class ServersConfig(val servers: Map<String, ServerConfig>)

    @Test
    fun `can load a simple config`() {
        class App(konfig: io.typestream.konfig.Konfig) {
            val serversConfig by konfig.inject<ServersConfig>()
        }

        val konfig = io.typestream.konfig.Konfig(FileInputStream("src/test/resources/map.properties"))
        val app = App(konfig)

        assertThat(app.serversConfig.servers["local"]).extracting("host", "port").containsExactly("localhost", 4242)
        assertThat(app.serversConfig.servers["remote"]).extracting("host", "port").containsExactly("example.com", 2424)
    }
}
