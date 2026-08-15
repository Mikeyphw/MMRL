package com.dergoogler.mmrl.installer

/** Pure dependency graph validation/topological ordering used by bulk install preflight. */
object DependencyPlanPolicy {
    data class Node(
        val id: String,
        val dependencies: Set<String> = emptySet(),
        val compatible: Boolean = true,
        val alreadyInstalled: Boolean = false,
    )

    data class Plan(val orderedIds: List<String>)

    fun plan(rootIds: Collection<String>, nodes: Map<String, Node>): Plan {
        require(rootIds.isNotEmpty()) { "Install batch is empty" }
        val visiting = linkedSetOf<String>()
        val visited = linkedSetOf<String>()
        val ordered = mutableListOf<String>()

        fun visit(id: String, chain: List<String>) {
            if (id in visited) return
            require(id !in visiting) { "Dependency cycle: ${(chain + id).joinToString(" -> ")}" }
            val node = nodes[id] ?: error("Missing required dependency: $id")
            require(node.compatible) { "Required module is incompatible with this device/root manager: $id" }
            visiting += id
            node.dependencies.sorted().forEach { dep -> visit(dep, chain + id) }
            visiting -= id
            visited += id
            if (!node.alreadyInstalled) ordered += id
        }

        rootIds.distinct().sorted().forEach { visit(it, emptyList()) }
        return Plan(ordered)
    }
}
