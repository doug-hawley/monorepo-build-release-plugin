package io.github.doughawley.monorepo.build.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ProjectMetadataTest : FunSpec({

    test("hasChanges returns true when project has changed files") {
        // given
        val metadata = ProjectMetadata(
            name = "test-project",
            fullyQualifiedName = ":test-project",
            changedFiles = listOf("src/main/File.kt", "build.gradle.kts")
        )

        // then
        metadata.hasChanges() shouldBe true
    }

    test("hasChanges returns false when project has no changed files") {
        // given
        val metadata = ProjectMetadata(
            name = "test-project",
            fullyQualifiedName = ":test-project",
            changedFiles = emptyList()
        )

        // then
        metadata.hasChanges() shouldBe false
    }

    test("hasChanges returns true when dependency has changes") {
        // given
        val dependency = ProjectMetadata(
            name = "dependency",
            fullyQualifiedName = ":dependency",
            changedFiles = listOf("dependency/File.kt")
        )

        val metadata = ProjectMetadata(
            name = "test-project",
            fullyQualifiedName = ":test-project",
            dependencies = listOf(dependency),
            changedFiles = emptyList()
        )

        // then - should return true because dependency has changes
        metadata.hasChanges() shouldBe true
    }

    test("hasChanges returns true when transitive dependency has changes") {
        // given
        val nestedDep = ProjectMetadata(
            name = "nested-dep",
            fullyQualifiedName = ":nested-dep",
            changedFiles = listOf("nested/File.kt")
        )

        val dependency = ProjectMetadata(
            name = "dependency",
            fullyQualifiedName = ":dependency",
            dependencies = listOf(nestedDep),
            changedFiles = emptyList()
        )

        val metadata = ProjectMetadata(
            name = "test-project",
            fullyQualifiedName = ":test-project",
            dependencies = listOf(dependency),
            changedFiles = emptyList()
        )

        // then - should return true because transitive dependency has changes
        metadata.hasChanges() shouldBe true
    }

    test("hasDirectChanges returns true only for direct changes") {
        // given
        val dependency = ProjectMetadata(
            name = "dependency",
            fullyQualifiedName = ":dependency",
            changedFiles = listOf("dependency/File.kt")
        )

        val metadata = ProjectMetadata(
            name = "test-project",
            fullyQualifiedName = ":test-project",
            dependencies = listOf(dependency),
            changedFiles = emptyList()
        )

        // then
        metadata.hasChanges() shouldBe true  // dependency has changes
        metadata.hasDirectChanges() shouldBe false  // but no direct changes
    }

    test("hasChanges terminates correctly for deep dependency trees") {
        // ProjectMetadata uses immutable val fields, so true circular references
        // are not constructible. This test verifies that deeply nested trees
        // are traversed to completion without infinite recursion.
        fun buildChain(depth: Int, hasChange: Boolean): ProjectMetadata {
            if (depth == 0) {
                return ProjectMetadata(
                    name = "leaf",
                    fullyQualifiedName = ":leaf",
                    changedFiles = if (hasChange) listOf("leaf/File.kt") else emptyList()
                )
            }
            return ProjectMetadata(
                name = "level-$depth",
                fullyQualifiedName = ":level-$depth",
                dependencies = listOf(buildChain(depth - 1, hasChange))
            )
        }

        // A deep chain with a change at the leaf should propagate to the root
        buildChain(20, hasChange = true).hasChanges() shouldBe true

        // A deep chain with no changes should return false all the way up
        buildChain(20, hasChange = false).hasChanges() shouldBe false
    }

    test("hasChanges handles diamond dependency graphs without double-counting") {
        // given: A → B, A → C, B → D, C → D  (diamond shape, D has changes)
        val sharedDep = ProjectMetadata(
            name = "shared",
            fullyQualifiedName = ":shared",
            changedFiles = listOf("shared/File.kt")
        )

        val depB = ProjectMetadata(
            name = "dep-b",
            fullyQualifiedName = ":dep-b",
            dependencies = listOf(sharedDep),
            changedFiles = emptyList()
        )

        val depC = ProjectMetadata(
            name = "dep-c",
            fullyQualifiedName = ":dep-c",
            dependencies = listOf(sharedDep),
            changedFiles = emptyList()
        )

        val root = ProjectMetadata(
            name = "root",
            fullyQualifiedName = ":root",
            dependencies = listOf(depB, depC),
            changedFiles = emptyList()
        )

        // then — visited tracking avoids re-traversing :shared via depC
        root.hasChanges() shouldBe true
    }

    test("hasChanges returns false for diamond dependency graph with no changes") {
        // given: same diamond shape but no changes anywhere
        val sharedDep = ProjectMetadata(
            name = "shared",
            fullyQualifiedName = ":shared",
            changedFiles = emptyList()
        )

        val depB = ProjectMetadata(
            name = "dep-b",
            fullyQualifiedName = ":dep-b",
            dependencies = listOf(sharedDep),
            changedFiles = emptyList()
        )

        val depC = ProjectMetadata(
            name = "dep-c",
            fullyQualifiedName = ":dep-c",
            dependencies = listOf(sharedDep),
            changedFiles = emptyList()
        )

        val root = ProjectMetadata(
            name = "root",
            fullyQualifiedName = ":root",
            dependencies = listOf(depB, depC),
            changedFiles = emptyList()
        )

        // then
        root.hasChanges() shouldBe false
    }

    test("toString includes dependency count and file count") {
        // given
        val dep1 = ProjectMetadata("dep1", ":dep1")
        val dep2 = ProjectMetadata("dep2", ":dep2")

        val metadata = ProjectMetadata(
            name = "test-project",
            fullyQualifiedName = ":test-project",
            dependencies = listOf(dep1, dep2),
            changedFiles = listOf("file1.kt", "file2.kt", "file3.kt")
        )

        // when
        val result = metadata.toString()

        // then
        result shouldBe "ProjectMetadata(name='test-project', fullyQualifiedName=':test-project', dependencies=2, changedFiles=3 files)"
    }
})
