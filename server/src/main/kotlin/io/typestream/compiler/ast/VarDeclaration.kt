package io.typestream.compiler.ast

import io.typestream.compiler.lexer.Token

data class VarDeclaration(val token: Token, val expr: Expr) : Statement {
    override fun <K> accept(visitor: Statement.Visitor<K>) = visitor.visitVarDeclaration(this)
}
