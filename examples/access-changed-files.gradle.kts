// Example: How to access changed files information
// Place this in your root build.gradle.kts

plugins {
    id("io.github.doug-hawley.monorepo-build-release-plugin") version "0.3.3" // x-release-please-version
}

monorepo {
    build {
        baseBranch = "main"
        includeUntracked = true
    }
}

// The plugin computes results during the configuration phase, so any task can read
// from the extension directly — no dependsOn("printChangedProjectsFromBranch") needed.

// Example 1: Simple - Just list changed projects
tasks.register("listChangedProjects") {
    doLast {
        val extension = project.extensions.getByType(
            io.github.doughawley.monorepo.MonorepoExtension::class.java
        ).build
        println("Changed projects: ${extension.allAffectedProjects.joinToString(", ")}")
    }
}

// Example 2: Detailed - List changed files per project
tasks.register("listChangedFiles") {
    doLast {
        val extension = project.extensions.getByType(
            io.github.doughawley.monorepo.MonorepoExtension::class.java
        ).build

        println("Changed files by project:")
        extension.changedFilesMap.forEach { (projectPath, files) ->
            println("\n$projectPath (${files.size} files):")
            files.forEach { file ->
                println("  - $file")
            }
        }
    }
}

// Example 3: Advanced - Use full metadata with dependencies
tasks.register("analyzeChanges") {
    doLast {
        val extension = project.extensions.getByType(
            io.github.doughawley.monorepo.MonorepoExtension::class.java
        ).build

        println("Detailed change analysis:")
        extension.metadataMap.values
            .filter { it.hasChanges() }
            .forEach { metadata ->
                println("\n${metadata.fullyQualifiedName}:")
                println("  Name: ${metadata.name}")
                println("  Direct changes: ${metadata.changedFiles.size} files")
                println("  Including dependencies: ${metadata.getAllChangedFilesIncludingDependencies().size} files")
                println("  Dependencies: ${metadata.dependencies.map { it.name }.joinToString(", ")}")
                println("  Changed files:")
                metadata.changedFiles.forEach { file ->
                    println("    - $file")
                }
            }
    }
}

// Example 4: Conditional build based on file types
tasks.register("smartBuild") {
    doLast {
        val extension = project.extensions.getByType(
            io.github.doughawley.monorepo.MonorepoExtension::class.java
        ).build

        extension.changedFilesMap.forEach { (projectPath, files) ->
            val hasSourceChanges = files.any { it.endsWith(".kt") || it.endsWith(".java") }
            val hasTestChanges = files.any { it.contains("/test/") }
            val hasConfigChanges = files.any { it.endsWith("build.gradle.kts") || it.endsWith(".properties") }

            println("\n$projectPath:")
            println("  Source changes: $hasSourceChanges")
            println("  Test changes: $hasTestChanges")
            println("  Config changes: $hasConfigChanges")

            // Make decisions based on what changed
            if (hasSourceChanges) {
                println("  → Running full build")
            } else if (hasTestChanges) {
                println("  → Running tests only")
            } else if (hasConfigChanges) {
                println("  → Running configuration validation")
            }
        }
    }
}

// Example 5: Generate impact report
tasks.register("impactReport") {
    doLast {
        val extension = project.extensions.getByType(
            io.github.doughawley.monorepo.MonorepoExtension::class.java
        ).build

        val report = StringBuilder()
        report.appendLine("=" .repeat(80))
        report.appendLine("CHANGE IMPACT REPORT")
        report.appendLine("=" .repeat(80))
        report.appendLine()

        val changedProjects = extension.metadataMap.values.filter { it.hasChanges() }
        report.appendLine("Total projects with changes: ${changedProjects.size}")
        report.appendLine()

        changedProjects.forEach { metadata ->
            report.appendLine("-".repeat(80))
            report.appendLine("Project: ${metadata.fullyQualifiedName}")
            report.appendLine("Changed files: ${metadata.changedFiles.size}")
            report.appendLine()

            if (metadata.dependencies.isNotEmpty()) {
                report.appendLine("Dependencies (${metadata.dependencies.size}):")
                metadata.dependencies.forEach { dep ->
                    val depChanges = if (dep.hasChanges()) " [HAS CHANGES]" else ""
                    report.appendLine("  - ${dep.name}$depChanges")
                }
                report.appendLine()
            }

            val allFiles = metadata.getAllChangedFilesIncludingDependencies()
            report.appendLine("Total impact (including dependencies): ${allFiles.size} files")
            report.appendLine()
        }

        report.appendLine("=" .repeat(80))
        println(report.toString())

        // Optionally write to file
        file("build/change-impact-report.txt").writeText(report.toString())
        println("Report saved to: build/change-impact-report.txt")
    }
}
