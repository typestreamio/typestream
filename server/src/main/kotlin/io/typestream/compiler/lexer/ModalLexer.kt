package io.typestream.compiler.lexer

class ModalLexer(sourceScanner: SourceScanner) {
    private val stack = ArrayDeque<Tokenizer>(listOf(MainLexer(sourceScanner)))
    fun next() = stack.last().nextToken(stack)
}
