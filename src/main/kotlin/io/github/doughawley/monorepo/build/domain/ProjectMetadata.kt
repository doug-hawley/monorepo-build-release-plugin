package io.github.doughawley.monorepo.build.domain

data class ProjectMetadata(
    val name: String,
    val fullyQualifiedName: String,
    val dependencies: List<ProjectMetadata> = emptyList(),
    val changedFiles: List<String> = emptyList()
) {
    /**
     * Returns true if this project has changed files OR any of its dependencies have changes (recursively).
     */
    fun hasChanges(): Boolean {
        return hasChanges(mutableSetOf())
    }

    private fun hasChanges(visited: MutableSet<String>): Boolean {
        if (!visited.add(fullyQualifiedName)) {
            return false
        }

        // Check if this project has direct changes
        if (changedFiles.isNotEmpty()) {
            return true
        }

        // Check if any dependency has changes (recursively)
        return dependencies.any { it.hasChanges(visited) }
    }

    /**
     * Returns true if this project has direct changed files (not including dependency changes).
     */
    fun hasDirectChanges(): Boolean = changedFiles.isNotEmpty()

    override fun toString(): String {
        return "ProjectMetadata(name='$name', fullyQualifiedName='$fullyQualifiedName', dependencies=${dependencies.size}, changedFiles=${changedFiles.size} files)"
    }
}
