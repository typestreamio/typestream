package io.typestream.filesystem

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class InodeTest {

    @Nested
    inner class FindInode {
        @Test
        fun `finds dir`() {
            val rootDir = Directory("/")
            val devDir = Directory("dev")

            rootDir.add(devDir)

            assertEquals(devDir, rootDir.findInode("/dev"))
        }

        @Test
        fun `finds nested dir`() {
            val rootDir = Directory("/")
            val devDir = Directory("dev")
            val kafkaDir = Directory("kafka")
            devDir.add(kafkaDir)
            rootDir.add(devDir)

            assertEquals(kafkaDir, rootDir.findInode("/dev/kafka"))
        }

        @Test
        fun `finds root dir`() {
            val rootDir = Directory("/")
            assertEquals(rootDir, rootDir.findInode("/"))
        }
    }

    @Test
    fun `builds path`() {
        val rootDir = Directory("/")
        val devDir = Directory("dev")
        val kafkaDir = Directory("kafka")
        devDir.add(kafkaDir)
        rootDir.add(devDir)

        assertEquals("/", rootDir.path())
        assertEquals("/dev", devDir.path())
        assertEquals("/dev/kafka", kafkaDir.path())
    }
}
