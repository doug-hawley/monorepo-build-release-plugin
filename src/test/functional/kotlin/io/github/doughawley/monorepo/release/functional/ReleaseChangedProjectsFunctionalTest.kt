package io.github.doughawley.monorepo.release.functional

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.testkit.runner.TaskOutcome
import java.io.File

class ReleaseChangedProjectsFunctionalTest : FunSpec({

    val testListener = listener(ReleaseTestProjectListener())

    // ─────────────────────────────────────────────────────────────
    // No changed projects
    // ─────────────────────────────────────────────────────────────

    test("succeeds with no tags created when no projects have changed") {
        // given: two commits so HEAD~1 exists; second commit only touches a root file
        val project = StandardReleaseTestProject.createMultiProjectAndInitialize(testListener.getTestProjectDir())
        project.modifyFile("gradle.properties", "# updated")
        project.commitAll("Update root file")

        // when: HEAD~1 diff covers only the root file — no subproject changes
        val result = project.runTask(
            "releaseChangedProjects",
            properties = mapOf("monorepo.commitRef" to "HEAD~1")
        )

        // then
        result.task(":releaseChangedProjects")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteTags() shouldBe emptyList()
    }

    // ─────────────────────────────────────────────────────────────
    // Single project changed
    // ─────────────────────────────────────────────────────────────

    test("releases only the changed opted-in project") {
        // given
        val project = StandardReleaseTestProject.createMultiProjectAndInitialize(testListener.getTestProjectDir())
        project.modifyFile("app/app.txt", "changed")
        project.commitAll("Change app")

        // when
        val result = project.runTask(
            "releaseChangedProjects",
            properties = mapOf("monorepo.commitRef" to "HEAD~1")
        )

        // then: only app is released; lib is untouched
        result.task(":releaseChangedProjects")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteTags() shouldContain "release/app/v0.1.0"
        project.remoteTags() shouldNotContain "release/lib/v0.1.0"
    }

    // ─────────────────────────────────────────────────────────────
    // Both projects changed
    // ─────────────────────────────────────────────────────────────

    test("releases all changed opted-in projects and creates release branches") {
        // given
        val project = StandardReleaseTestProject.createMultiProjectAndInitialize(testListener.getTestProjectDir())
        project.modifyFile("app/app.txt", "changed")
        project.modifyFile("lib/lib.txt", "changed")
        project.commitAll("Change both")

        // when
        val result = project.runTask(
            "releaseChangedProjects",
            properties = mapOf("monorepo.commitRef" to "HEAD~1")
        )

        // then
        result.task(":releaseChangedProjects")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteTags() shouldContain "release/app/v0.1.0"
        project.remoteTags() shouldContain "release/lib/v0.1.0"
        project.remoteBranches() shouldContain "release/app/v0.1.x"
        project.remoteBranches() shouldContain "release/lib/v0.1.x"
    }

    // ─────────────────────────────────────────────────────────────
    // Opt-in model
    // ─────────────────────────────────────────────────────────────

    test("skips project with enabled=false even when it has changed") {
        // given: lib has enabled=false, both projects changed
        val project = StandardReleaseTestProject.createMultiProjectAndInitialize(
            testListener.getTestProjectDir(),
            libEnabled = false
        )
        project.modifyFile("app/app.txt", "changed")
        project.modifyFile("lib/lib.txt", "changed")
        project.commitAll("Change both")

        // when
        val result = project.runTask(
            "releaseChangedProjects",
            properties = mapOf("monorepo.commitRef" to "HEAD~1")
        )

        // then: only app released; lib skipped because it is not opted in
        result.task(":releaseChangedProjects")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteTags() shouldContain "release/app/v0.1.0"
        project.remoteTags() shouldNotContain "release/lib/v0.1.0"
    }

    // ─────────────────────────────────────────────────────────────
    // Scope override
    // ─────────────────────────────────────────────────────────────

    test("primaryBranchScope=major bumps both projects to v1.0.0") {
        // given: both projects have a prior v0.1.0 release; scope configured to major
        val project = StandardReleaseTestProject.createMultiProjectAndInitialize(
            testListener.getTestProjectDir(),
            primaryBranchScope = "major"
        )
        project.createTag("release/app/v0.1.0")
        project.pushTag("release/app/v0.1.0")
        project.createTag("release/lib/v0.1.0")
        project.pushTag("release/lib/v0.1.0")
        project.modifyFile("app/app.txt", "changed")
        project.modifyFile("lib/lib.txt", "changed")
        project.commitAll("Change both")

        // when
        val result = project.runTask(
            "releaseChangedProjects",
            properties = mapOf("monorepo.commitRef" to "HEAD~1")
        )

        // then: major bump → v1.0.0 for both
        result.task(":releaseChangedProjects")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteTags() shouldContain "release/app/v1.0.0"
        project.remoteTags() shouldContain "release/lib/v1.0.0"
    }

    // ─────────────────────────────────────────────────────────────
    // Tag collision resilience (--continue)
    // ─────────────────────────────────────────────────────────────

    // ─────────────────────────────────────────────────────────────
    // Nothing to release
    // ─────────────────────────────────────────────────────────────

    test("changed projects with all disabled emits nothing-to-release log and creates no tags") {
        // given: both :app and :lib have enabled = false
        val projectDir = testListener.getTestProjectDir()
        val remoteDir = File(projectDir.parentFile, "${projectDir.name}-remote.git")

        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("io.github.doug-hawley.monorepo-build-release-plugin")
            }

            monorepo {
                release {
                    primaryBranchScope = "minor"
                }
            }
            """.trimIndent()
        )
        File(projectDir, "settings.gradle.kts").writeText(
            """
            rootProject.name = "test-project"
            include(":app")
            include(":lib")
            """.trimIndent()
        )
        File(projectDir, ".gitignore").writeText(".gradle/\nbuild/")

        val appDir = File(projectDir, "app")
        appDir.mkdirs()
        File(appDir, "build.gradle.kts").writeText(
            """
            monorepoProject {
                release {
                    enabled = false
                }
            }

            tasks.register("build") {
                doLast {
                    val libsDir = layout.buildDirectory.dir("libs").get().asFile
                    libsDir.mkdirs()
                    java.io.File(libsDir, "${'$'}{project.name}.jar").writeText("built artifact")
                }
            }
            """.trimIndent()
        )
        File(appDir, "app.txt").writeText("app source")

        val libDir = File(projectDir, "lib")
        libDir.mkdirs()
        File(libDir, "build.gradle.kts").writeText(
            """
            monorepoProject {
                release {
                    enabled = false
                }
            }

            tasks.register("build") {
                doLast {
                    val libsDir = layout.buildDirectory.dir("libs").get().asFile
                    libsDir.mkdirs()
                    java.io.File(libsDir, "${'$'}{project.name}.jar").writeText("built artifact")
                }
            }
            """.trimIndent()
        )
        File(libDir, "lib.txt").writeText("lib source")

        val project = ReleaseTestProject(projectDir, remoteDir)
        project.initGit()
        project.commitAll("Initial commit")
        project.pushToRemote()
        project.modifyFile("app/app.txt", "changed")
        project.modifyFile("lib/lib.txt", "changed")
        project.commitAll("Change both")

        // when
        val result = project.runTask(
            "releaseChangedProjects",
            properties = mapOf("monorepo.commitRef" to "HEAD~1")
        )

        // then
        result.task(":releaseChangedProjects")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteTags() shouldBe emptyList()
        result.output shouldContain "nothing to release"
    }

    // ─────────────────────────────────────────────────────────────
    // postRelease lifecycle
    // ─────────────────────────────────────────────────────────────

    test("postRelease runs for all released projects") {
        // given: both :app and :lib changed
        val project = StandardReleaseTestProject.createMultiProjectAndInitialize(testListener.getTestProjectDir())
        project.modifyFile("app/app.txt", "changed")
        project.modifyFile("lib/lib.txt", "changed")
        project.commitAll("Change both")

        // when
        val result = project.runTask(
            "releaseChangedProjects",
            properties = mapOf("monorepo.commitRef" to "HEAD~1")
        )

        // then: both postRelease tasks ran (UP_TO_DATE = no-op task ran, not SKIPPED due to failure)
        result.task(":releaseChangedProjects")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":app:postRelease")?.outcome shouldBe TaskOutcome.UP_TO_DATE
        result.task(":lib:postRelease")?.outcome shouldBe TaskOutcome.UP_TO_DATE
    }

    test("tag collision on one project does not prevent the other from releasing") {
        // given: both projects changed; a pre-existing local tag for app causes its release to fail
        val project = StandardReleaseTestProject.createMultiProjectAndInitialize(testListener.getTestProjectDir())
        project.modifyFile("app/app.txt", "changed")
        project.modifyFile("lib/lib.txt", "changed")
        project.commitAll("Change both")

        // Pre-create a local tag that will collide: scanner sees no remote tag → next = v0.1.0
        // but tagExists check finds the local tag → app:release fails without pushing
        project.createTag("release/app/v0.1.0")

        // when: --continue lets lib:release run despite app:release failing
        val result = project.runTaskAndFail(
            "releaseChangedProjects", "--continue",
            properties = mapOf("monorepo.commitRef" to "HEAD~1")
        )

        // then: lib was released; app was not (no remote push)
        result.task(":lib:release")?.outcome shouldBe TaskOutcome.SUCCESS
        project.remoteTags() shouldContain "release/lib/v0.1.0"
        project.remoteTags() shouldNotContain "release/app/v0.1.0"
    }
})
