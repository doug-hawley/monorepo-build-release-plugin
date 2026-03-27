package io.github.doughawley.monorepo.build.functional

import io.github.doughawley.monorepo.build.functional.StandardTestProject.Files
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.testkit.runner.TaskOutcome

/**
 * Functional tests for the per-subproject buildChanged task.
 *
 * Unlike the root-level buildChanged (which builds ALL affected projects),
 * the per-subproject buildChanged builds the project itself and runs build
 * (including tests) on any changed transitive upstream dependencies.
 */
class PerProjectBuildChangedFunctionalTest : FunSpec({
    val testProjectListener = extension(TestProjectListener())

    test("per-project buildChanged builds only self when no upstream deps have changed") {
        // given
        val project = testProjectListener.createStandardProject()
        project.appendToFile(Files.APP1_SOURCE, "\n// Modified")
        project.commitAll("Change app1")

        // when
        val result = project.runTask(":apps:app1:buildChanged")

        // then
        result.task(":apps:app1:buildChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":apps:app1:build")?.outcome shouldBe TaskOutcome.SUCCESS
        // upstream deps only assembled, not built
        result.task(":modules:module1:build") shouldBe null
        result.task(":modules:module2:build") shouldBe null
        result.task(":common-lib:build") shouldBe null
    }

    test("per-project buildChanged runs build on changed direct upstream dependency") {
        // given
        val project = testProjectListener.createStandardProject()
        project.appendToFile(Files.COMMON_LIB_SOURCE, "\n// Modified")
        project.commitAll("Change common-lib")

        // when
        val result = project.runTask(":modules:module1:buildChanged")

        // then
        result.task(":modules:module1:buildChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":modules:module1:build")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":common-lib:build")?.outcome shouldBe TaskOutcome.SUCCESS
    }

    test("per-project buildChanged runs build on all changed transitive upstream deps") {
        // given: common-lib changed → module1, module2 affected (they depend on common-lib)
        //        app1 depends on module1, module2, common-lib — all are affected
        val project = testProjectListener.createStandardProject()
        project.appendToFile(Files.COMMON_LIB_SOURCE, "\n// Modified")
        project.commitAll("Change common-lib")

        // when
        val result = project.runTask(":apps:app1:buildChanged")

        // then: app1, common-lib, module1, module2 all get build
        result.task(":apps:app1:buildChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":apps:app1:build")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":common-lib:build")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":modules:module1:build")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":modules:module2:build")?.outcome shouldBe TaskOutcome.SUCCESS
    }

    test("per-project buildChanged runs build only on changed upstream deps not unchanged ones") {
        // given: only module1 changed — app1 depends on module1 and module2
        val project = testProjectListener.createStandardProject()
        project.appendToFile(Files.MODULE1_SOURCE, "\n// Modified")
        project.commitAll("Change module1")

        // when
        val result = project.runTask(":apps:app1:buildChanged")

        // then
        result.task(":apps:app1:buildChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":apps:app1:build")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":modules:module1:build")?.outcome shouldBe TaskOutcome.SUCCESS
        // module2 is a dep of app1 but NOT changed, so only assembled
        result.task(":modules:module2:build") shouldBe null
    }

    test("per-project buildChanged does not run build on unchanged upstream dependency") {
        // given: only module1 changed — common-lib is an upstream dep but not changed
        val project = testProjectListener.createStandardProject()
        project.appendToFile(Files.MODULE1_SOURCE, "\n// Modified")
        project.commitAll("Change module1")

        // when
        val result = project.runTask(":modules:module1:buildChanged")

        // then
        result.task(":modules:module1:buildChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":modules:module1:build")?.outcome shouldBe TaskOutcome.SUCCESS
        // common-lib is upstream of module1 but NOT changed
        result.task(":common-lib:build") shouldBe null
    }

    test("per-project buildChanged treats all upstream deps as changed when no baseline exists") {
        // given: no remote — all projects treated as changed
        val project = StandardTestProject.createAndInitialize(
            testProjectListener.getTestProjectDir(),
            withRemote = false
        )

        // when
        val result = project.runTask(":apps:app1:buildChanged")

        // then: all upstream deps of app1 get build
        result.task(":apps:app1:buildChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":apps:app1:build")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":common-lib:build")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":modules:module1:build")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":modules:module2:build")?.outcome shouldBe TaskOutcome.SUCCESS
        result.output shouldContain "no baseline"
    }

    test("per-project buildChanged builds all upstream deps when BOM changes") {
        // given: platform changed → all projects affected
        val project = testProjectListener.createStandardProject()
        project.appendToFile(Files.PLATFORM_BUILD, "\n// BOM version bump")
        project.commitAll("Bump BOM version")

        // when
        val result = project.runTask(":apps:app1:buildChanged")

        // then: all upstream deps of app1 are affected
        result.task(":apps:app1:buildChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":apps:app1:build")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":common-lib:build")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":modules:module1:build")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":modules:module2:build")?.outcome shouldBe TaskOutcome.SUCCESS
    }

    test("per-project buildChanged ignores changes in unrelated projects") {
        // given: app2 changed but app1 does not depend on app2
        val project = testProjectListener.createStandardProject()
        project.appendToFile(Files.APP2_SOURCE, "\n// Modified")
        project.commitAll("Change app2")

        // when
        val result = project.runTask(":apps:app1:buildChanged")

        // then: only app1 is built (app2 is not a dep)
        result.task(":apps:app1:buildChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":apps:app1:build")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":apps:app2:build") shouldBe null
        result.task(":modules:module1:build") shouldBe null
        result.task(":modules:module2:build") shouldBe null
        result.task(":common-lib:build") shouldBe null
    }

    test("per-project buildChanged on project with no changed upstream deps only runs own build") {
        // given: common-lib changed (common-lib depends on platform, which is not changed)
        val project = testProjectListener.createStandardProject()
        project.appendToFile(Files.COMMON_LIB_SOURCE, "\n// Modified")
        project.commitAll("Change common-lib")

        // when
        val result = project.runTask(":common-lib:buildChanged")

        // then: only common-lib is built (platform is not changed)
        result.task(":common-lib:buildChanged")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":common-lib:build")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":platform:build") shouldBe null
    }
})
