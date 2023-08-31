package io.typestream.compiler.ast

import kotlinx.serialization.Serializable

@Serializable
sealed interface Statement {
    interface Visitor<K> {
        fun visitVarDeclaration(varDeclaration: VarDeclaration): K
        fun visitShellCommand(shellCommand: ShellCommand): K
        fun visitDataCommand(dataCommand: DataCommand): K
        fun visitPipeline(pipeline: Pipeline): K
    }

    fun <K> accept(visitor: Visitor<K>): K
}
