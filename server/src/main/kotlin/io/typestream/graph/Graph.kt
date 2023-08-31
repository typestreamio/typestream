package io.typestream.graph

import java.util.function.Predicate

class Graph<K>(val ref: K) {
    val children = mutableSetOf<Graph<K>>()
    private val parents = mutableSetOf<Graph<K>>()

    fun addChild(child: Graph<K>) {
        children.add(child)
        child.parents.add(this)
    }

    private fun print(node: Graph<K> = this, indent: Int = 1): String = buildString {
        append(node)
        append("\n")
        for (child in node.children) {
            append("\t".repeat(indent))
            append(print(child, indent + 1))
        }
    }

    fun walk(visitor: (Graph<K>) -> Unit) {
        DepthFirstVisitor(this).walk(this, visitor)
    }

    fun findChildren(predicate: Predicate<Graph<K>>): Set<Graph<K>> {
        val nodes = this.children.flatMap {
            if (predicate.test(it)) {
                listOf(it)
            } else {
                it.findChildren(predicate)
            }
        }
        return nodes.toSet()
    }

    fun findLeaves() = findChildren { it.children.isEmpty() }

    override fun toString(): String = ref.toString()

    internal class DepthFirstVisitor<K>(private val root: Graph<K>) {
        private val stack = ArrayDeque(listOf(root))
        private var currentNode = root
        private val visited = mutableSetOf<Graph<K>>()

        fun walk(rootNode: Graph<K> = root, visit: (Graph<K>) -> Unit) {
            for (child in rootNode.children) {
                stack.addFirst(child)
            }

            while (stack.isNotEmpty()) {
                currentNode = stack.removeFirst()
                if (!visited.contains(currentNode)) {
                    visit(currentNode)
                    visited.add(currentNode)
                }
                walk(currentNode, visit)
            }
        }
    }

}
