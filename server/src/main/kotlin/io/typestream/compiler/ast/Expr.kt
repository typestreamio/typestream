package io.typestream.compiler.ast

import io.typestream.compiler.lexer.Token
import io.typestream.compiler.lexer.TokenType
import io.typestream.compiler.types.Value
import kotlinx.serialization.Serializable

@Serializable
sealed class Expr {
    interface Visitor<K> {
        fun visitAssign(assign: Assign): K
        fun visitBareWord(bareWord: BareWord): K
        fun visitLiteral(literal: Literal): K
        fun visitVariable(variable: Variable): K
        fun visitBinary(binary: Binary): K
        fun visitGrouping(grouping: Grouping): K
        fun visitBlock(block: Block): K
    }

    abstract fun <K> accept(visitor: Visitor<K>): K

    @Serializable
    data class Assign(val name: Token, val value: Expr) : Expr() {
        override fun <K> accept(visitor: Visitor<K>): K = visitor.visitAssign(this)
    }

    @Serializable
    data class Variable(val name: Token) : Expr() {
        override fun <K> accept(visitor: Visitor<K>): K = visitor.visitVariable(this)
    }

    @Serializable
    data class Binary(val left: Expr, val operator: TokenType, val right: Expr) : Expr() {
        override fun <K> accept(visitor: Visitor<K>): K = visitor.visitBinary(this)
    }

    @Serializable
    data class Grouping(val expr: Binary) : Expr() {
        override fun <K> accept(visitor: Visitor<K>): K = visitor.visitGrouping(this)
    }

    @Serializable
    data class BareWord(val value: String) : Expr() {
        override fun <K> accept(visitor: Visitor<K>): K = visitor.visitBareWord(this)
    }

    @Serializable
    data class Literal(val value: Value) : Expr() {
        override fun <K> accept(visitor: Visitor<K>): K = visitor.visitLiteral(this)
    }

    @Serializable
    data class Block(val argument: Token, val pipeline: Pipeline) : Expr() {
        override fun <K> accept(visitor: Visitor<K>): K = visitor.visitBlock(this)
    }

    @Serializable
    data class Incomplete(val argument: Token) : Expr() {
        override fun <K> accept(visitor: Visitor<K>): K {
            error("Incomplete expressions should not be visited.")
        }
    }
}
