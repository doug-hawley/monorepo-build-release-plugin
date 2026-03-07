package io.github.doughawley.monorepo.release.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private data class FormatTagCase(
    val globalPrefix: String,
    val projectPrefix: String,
    val version: SemanticVersion,
    val expected: String
)

class TagPatternTest : FunSpec({

    context("formatTag produces globalPrefix/projectPrefix/vVersion") {
        withData(
            FormatTagCase("release", "api", SemanticVersion(1, 2, 0), "release/api/v1.2.0"),
            FormatTagCase("custom-prefix", "service", SemanticVersion(0, 1, 0), "custom-prefix/service/v0.1.0"),
        ) { (globalPrefix, projectPrefix, version, expected) ->
            TagPattern.formatTag(globalPrefix, projectPrefix, version) shouldBe expected
        }
    }

    context("formatReleaseBranch produces globalPrefix/projectPrefix/vMajor.Minor.x") {
        withData(
            Triple("api", SemanticVersion(1, 2, 0), "release/api/v1.2.x"),
            Triple("app", SemanticVersion(0, 1, 0), "release/app/v0.1.x"),
        ) { (projectPrefix, version, expected) ->
            TagPattern.formatReleaseBranch("release", projectPrefix, version) shouldBe expected
        }
    }

    test("formatReleaseBranch uses the provided globalPrefix, not a hardcoded value") {
        TagPattern.formatReleaseBranch("deploy", "api", SemanticVersion(1, 0, 0)) shouldBe "deploy/api/v1.0.x"
    }

    context("deriveProjectTagPrefix strips leading colon and replaces inner colons with dashes") {
        withData(
            ":api" to "api",
            ":services:auth" to "services-auth",
            ":a:b:c" to "a-b-c",
        ) { (gradlePath, expected) ->
            TagPattern.deriveProjectTagPrefix(gradlePath) shouldBe expected
        }
    }

    test("parseVersionFromTag round-trips with formatTag") {
        // given
        val version = SemanticVersion(2, 3, 4)
        val tag = TagPattern.formatTag("release", "app", version)
        // when
        val parsed = TagPattern.parseVersionFromTag(tag, "release", "app")
        // then
        parsed shouldBe version
    }

    context("parseVersionFromTag returns null for mismatched or malformed tags") {
        withData(
            "other/app/v1.0.0",             // wrong global prefix
            "release/other-project/v1.0.0", // wrong project prefix
            "release/app/vnotaversion",      // malformed version
        ) { tag ->
            TagPattern.parseVersionFromTag(tag, "release", "app").shouldBeNull()
        }
    }

    context("isReleaseBranch with default 'release' prefix") {
        withData(
            "release/api/v1.2.x" to true,
            "release/services-auth/v0.1.x" to true,
            "main" to false,
            "master" to false,
            "feature/my-feature" to false,
            "release/api/v1.2.0" to false,  // tag pattern, not branch
        ) { (branch, expected) ->
            TagPattern.isReleaseBranch(branch, "release") shouldBe expected
        }
    }

    test("isReleaseBranch uses the provided globalPrefix, not a hardcoded value") {
        TagPattern.isReleaseBranch("deploy/api/v1.0.x", "deploy") shouldBe true
        TagPattern.isReleaseBranch("deploy/api/v1.0.x", "release") shouldBe false
    }

    context("parseVersionLineFromBranch extracts major and minor") {
        withData(
            Triple("release/api/v0.2.x", 0, 2),
            Triple("release/app/v1.10.x", 1, 10),
        ) { (branch, expectedMajor, expectedMinor) ->
            val (major, minor) = TagPattern.parseVersionLineFromBranch(branch)
            major shouldBe expectedMajor
            minor shouldBe expectedMinor
        }
    }

    context("parseVersionLineFromBranch throws on malformed branch names") {
        withData(
            "main",
            "feature/my-feature",
            "release/api/bad",
            "release/api/1.2.x",
        ) { branch ->
            val exception = shouldThrow<IllegalArgumentException> {
                TagPattern.parseVersionLineFromBranch(branch)
            }
            exception.message shouldContain branch
        }
    }

    context("parseProjectPrefixFromBranch extracts the project prefix") {
        withData(
            Triple("release/app/v0.1.x", "release", "app"),
            Triple("release/services-auth/v1.2.x", "release", "services-auth"),
            Triple("deploy/my-app/v0.3.x", "deploy", "my-app"),
        ) { (branch, globalPrefix, expectedPrefix) ->
            TagPattern.parseProjectPrefixFromBranch(branch, globalPrefix) shouldBe expectedPrefix
        }
    }
})
