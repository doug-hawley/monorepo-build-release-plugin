package io.github.doughawley.monorepo.release.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.api.GradleException

class DevTagPatternTest : FunSpec({

    context("formatDevBranch produces dev/projectPrefix/branchName") {
        withData(
            Triple("app", "feature-auth", "dev/app/feature-auth"),
            Triple("services/auth", "experiment", "dev/services/auth/experiment"),
            Triple("apps/some_domain-v1", "JIRA-1234", "dev/apps/some_domain-v1/JIRA-1234"),
        ) { (projectPrefix, branchName, expected) ->
            DevTagPattern.formatDevBranch(projectPrefix, branchName) shouldBe expected
        }
    }

    context("formatDevTag produces dev/projectPrefix/branchName/number") {
        withData(
            Triple("app", "feature-auth", 1) to "dev/app/feature-auth/1",
            Triple("app", "feature-auth", 10) to "dev/app/feature-auth/10",
            Triple("services/auth", "experiment", 100) to "dev/services/auth/experiment/100",
        ) { (input, expected) ->
            val (projectPrefix, branchName, number) = input
            DevTagPattern.formatDevTag(projectPrefix, branchName, number) shouldBe expected
        }
    }

    context("formatDevVersion produces branchName.number") {
        withData(
            Triple("feature-auth", 1, "feature-auth.1"),
            Triple("feature-auth", 2, "feature-auth.2"),
            Triple("JIRA-1234", 5, "JIRA-1234.5"),
        ) { (branchName, number, expected) ->
            DevTagPattern.formatDevVersion(branchName, number) shouldBe expected
        }
    }

    context("isDevBranch returns true for valid dev branches") {
        withData(
            "dev/app/feature-auth",
            "dev/services/auth/experiment",
            "dev/a/b",
        ) { branch ->
            DevTagPattern.isDevBranch(branch) shouldBe true
        }
    }

    context("isDevBranch returns false for non-dev branches") {
        withData(
            "main",
            "master",
            "feature/my-feature",
            "release/app/v1.0.x",
            "dev",
            "dev/app",
        ) { branch ->
            DevTagPattern.isDevBranch(branch) shouldBe false
        }
    }

    context("parseDevBranch extracts projectPrefix and branchName") {
        withData(
            "dev/app/feature-auth" to DevBranchInfo("app", "feature-auth"),
            "dev/services/auth/feature-x" to DevBranchInfo("services/auth", "feature-x"),
            "dev/a/b/c/my-branch" to DevBranchInfo("a/b/c", "my-branch"),
        ) { (branch, expected) ->
            DevTagPattern.parseDevBranch(branch) shouldBe expected
        }
    }

    context("parseDevBranch returns null for non-dev branches") {
        withData(
            "main",
            "release/app/v1.0.x",
            "dev",
            "dev/app",
            "dev/app/",
            "notdev/app/feature",
        ) { branch ->
            DevTagPattern.parseDevBranch(branch).shouldBeNull()
        }
    }

    context("parseDevTagNumber extracts integer from ls-remote output") {
        withData(
            Triple("abc123\trefs/tags/dev/app/feature-auth/1", "app", "feature-auth") to 1,
            Triple("def456\trefs/tags/dev/app/feature-auth/42", "app", "feature-auth") to 42,
            Triple("ghi789\trefs/tags/dev/services/auth/exp/3", "services/auth", "exp") to 3,
        ) { (input, expected) ->
            val (tagRef, projectPrefix, branchName) = input
            DevTagPattern.parseDevTagNumber(tagRef, projectPrefix, branchName) shouldBe expected
        }
    }

    context("parseDevTagNumber returns null for non-matching refs") {
        withData(
            "abc123\trefs/tags/dev/other/feature-auth/1",
            "abc123\trefs/tags/dev/app/other-branch/1",
            "abc123\trefs/tags/dev/app/feature-auth/notanumber",
            "bad-line-without-tab",
            "abc123\trefs/tags/release/app/v1.0.0",
        ) { tagRef ->
            DevTagPattern.parseDevTagNumber(tagRef, "app", "feature-auth").shouldBeNull()
        }
    }

    context("validateBranchName accepts valid names") {
        withData(
            "feature-auth",
            "my.feature",
            "JIRA-1234",
            "v2-migration",
            "a",
            "A123_test.name-ok",
            "123-feature",
            "0day-fix",
        ) { name ->
            // should not throw
            DevTagPattern.validateBranchName(name)
        }
    }

    context("validateBranchName throws for invalid names") {
        withData(
            "" to "blank",
            "   " to "blank",
            "has space" to "Invalid",
            "has/slash" to "Invalid",
            ".leading-dot" to "Invalid",
            "-leading-dash" to "Invalid",
            "_leading-underscore" to "Invalid",
            "has~tilde" to "Invalid",
            "has^caret" to "Invalid",
            "has:colon" to "Invalid",
            "has?question" to "Invalid",
            "has*star" to "Invalid",
            "has[bracket" to "Invalid",
        ) { (name, expectedInMessage) ->
            val exception = shouldThrow<GradleException> {
                DevTagPattern.validateBranchName(name)
            }
            exception.message shouldContain expectedInMessage
        }
    }

    test("parseDevBranch round-trips with formatDevBranch") {
        // given
        val projectPrefix = "services/auth"
        val branchName = "feature-x"
        val branch = DevTagPattern.formatDevBranch(projectPrefix, branchName)

        // when
        val parsed = DevTagPattern.parseDevBranch(branch)

        // then
        parsed shouldBe DevBranchInfo(projectPrefix, branchName)
    }
})
