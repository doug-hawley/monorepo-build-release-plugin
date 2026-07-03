package io.github.doughawley.monorepo.build.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.gradle.testfixtures.ProjectBuilder

class ProjectMetadataFactoryTest : FunSpec({

    test("should build metadata for root project with no dependencies") {
        // given
        val rootProject = ProjectBuilder.builder().build()
        val logger = rootProject.logger
        val factory = ProjectMetadataFactory(logger)

        // when
        val metadataMap = factory.buildProjectMetadataMap(rootProject)

        // then
        metadataMap.size shouldBe 1
        val rootMetadata = metadataMap[":"]
        rootMetadata shouldNotBe null
        rootMetadata!!.name shouldBe rootProject.name
        rootMetadata.fullyQualifiedName shouldBe ":"
        rootMetadata.dependencies shouldHaveSize 0
    }

    test("should build metadata for project with single subproject") {
        // given
        val rootProject = ProjectBuilder.builder().build()
        ProjectBuilder.builder()
            .withParent(rootProject)
            .withName("submodule")
            .build()
        val logger = rootProject.logger
        val factory = ProjectMetadataFactory(logger)

        // when
        val metadataMap = factory.buildProjectMetadataMap(rootProject)

        // then
        metadataMap.size shouldBe 2

        val rootMetadata = metadataMap[":"]
        rootMetadata shouldNotBe null
        rootMetadata!!.name shouldBe rootProject.name
        rootMetadata.fullyQualifiedName shouldBe ":"

        val subMetadata = metadataMap[":submodule"]
        subMetadata shouldNotBe null
        subMetadata!!.name shouldBe "submodule"
        subMetadata.fullyQualifiedName shouldBe ":submodule"
    }

    test("should build metadata with simple dependency relationship") {
        // given
        val rootProject = ProjectBuilder.builder().build()
        val commonLib = ProjectBuilder.builder()
            .withParent(rootProject)
            .withName("common-lib")
            .build()
        val service = ProjectBuilder.builder()
            .withParent(rootProject)
            .withName("service")
            .build()

        commonLib.pluginManager.apply("java-library")
        service.pluginManager.apply("java-library")
        service.dependencies.add("implementation", commonLib)

        val logger = rootProject.logger
        val factory = ProjectMetadataFactory(logger)

        // when
        val metadataMap = factory.buildProjectMetadataMap(rootProject)

        // then
        metadataMap.size shouldBe 3

        val serviceMetadata = metadataMap[":service"]
        serviceMetadata shouldNotBe null
        serviceMetadata!!.dependencies shouldHaveSize 1
        serviceMetadata.dependencies[0].name shouldBe "common-lib"
        serviceMetadata.dependencies[0].fullyQualifiedName shouldBe ":common-lib"
    }

    test("should build metadata with transitive dependencies") {
        // given
        val rootProject = ProjectBuilder.builder().build()
        val commonLib = ProjectBuilder.builder()
            .withParent(rootProject)
            .withName("common-lib")
            .build()
        val userService = ProjectBuilder.builder()
            .withParent(rootProject)
            .withName("user-service")
            .build()
        val adminUi = ProjectBuilder.builder()
            .withParent(rootProject)
            .withName("admin-ui")
            .build()

        commonLib.pluginManager.apply("java-library")
        userService.pluginManager.apply("java-library")
        adminUi.pluginManager.apply("java-library")

        userService.dependencies.add("implementation", commonLib)
        adminUi.dependencies.add("implementation", userService)

        val logger = rootProject.logger
        val factory = ProjectMetadataFactory(logger)

        // when
        val metadataMap = factory.buildProjectMetadataMap(rootProject)

        // then
        metadataMap.size shouldBe 4

        val userServiceMetadata = metadataMap[":user-service"]
        userServiceMetadata shouldNotBe null
        userServiceMetadata!!.dependencies shouldHaveSize 1
        userServiceMetadata.dependencies[0].fullyQualifiedName shouldBe ":common-lib"

        val adminUiMetadata = metadataMap[":admin-ui"]
        adminUiMetadata shouldNotBe null
        adminUiMetadata!!.dependencies shouldHaveSize 1
        adminUiMetadata.dependencies[0].fullyQualifiedName shouldBe ":user-service"
    }

    test("should build metadata with multiple dependencies") {
        // given
        val rootProject = ProjectBuilder.builder().build()
        val coreUtils = ProjectBuilder.builder()
            .withParent(rootProject)
            .withName("core-utils")
            .build()
        val userApi = ProjectBuilder.builder()
            .withParent(rootProject)
            .withName("user-api")
            .build()
        val orderApi = ProjectBuilder.builder()
            .withParent(rootProject)
            .withName("order-api")
            .build()
        val apiGateway = ProjectBuilder.builder()
            .withParent(rootProject)
            .withName("api-gateway")
            .build()

        coreUtils.pluginManager.apply("java-library")
        userApi.pluginManager.apply("java-library")
        orderApi.pluginManager.apply("java-library")
        apiGateway.pluginManager.apply("java-library")

        userApi.dependencies.add("implementation", coreUtils)
        orderApi.dependencies.add("implementation", coreUtils)
        apiGateway.dependencies.add("implementation", userApi)
        apiGateway.dependencies.add("implementation", orderApi)

        val logger = rootProject.logger
        val factory = ProjectMetadataFactory(logger)

        // when
        val metadataMap = factory.buildProjectMetadataMap(rootProject)

        // then
        metadataMap.size shouldBe 5

        val apiGatewayMetadata = metadataMap[":api-gateway"]
        apiGatewayMetadata shouldNotBe null
        apiGatewayMetadata!!.dependencies shouldHaveSize 2

        val dependencyNames = apiGatewayMetadata.dependencies.map { it.name }
        dependencyNames shouldContain "user-api"
        dependencyNames shouldContain "order-api"
    }

    test("should build single project metadata") {
        // given
        val rootProject = ProjectBuilder.builder().build()
        val commonLib = ProjectBuilder.builder()
            .withParent(rootProject)
            .withName("common-lib")
            .build()
        val service = ProjectBuilder.builder()
            .withParent(rootProject)
            .withName("service")
            .build()

        commonLib.pluginManager.apply("java-library")
        service.pluginManager.apply("java-library")
        service.dependencies.add("implementation", commonLib)

        val logger = rootProject.logger
        val factory = ProjectMetadataFactory(logger)

        // when
        val serviceMetadata = factory.buildProjectMetadata(service)

        // then
        serviceMetadata.name shouldBe "service"
        serviceMetadata.fullyQualifiedName shouldBe ":service"
        serviceMetadata.dependencies shouldHaveSize 1
        serviceMetadata.dependencies[0].name shouldBe "common-lib"
    }

    test("should handle project with no dependencies") {
        // given
        val rootProject = ProjectBuilder.builder().build()
        val standalone = ProjectBuilder.builder()
            .withParent(rootProject)
            .withName("standalone")
            .build()

        standalone.pluginManager.apply("java-library")

        val logger = rootProject.logger
        val factory = ProjectMetadataFactory(logger)

        // when
        val metadataMap = factory.buildProjectMetadataMap(rootProject)

        // then
        val standaloneMetadata = metadataMap[":standalone"]
        standaloneMetadata shouldNotBe null
        standaloneMetadata!!.dependencies shouldHaveSize 0
    }

    test("should list all dependency names") {
        // given
        val rootProject = ProjectBuilder.builder().build()
        val libA = ProjectBuilder.builder()
            .withParent(rootProject)
            .withName("lib-a")
            .build()
        val libB = ProjectBuilder.builder()
            .withParent(rootProject)
            .withName("lib-b")
            .build()
        val service = ProjectBuilder.builder()
            .withParent(rootProject)
            .withName("service")
            .build()

        libA.pluginManager.apply("java-library")
        libB.pluginManager.apply("java-library")
        service.pluginManager.apply("java-library")

        service.dependencies.add("implementation", libA)
        service.dependencies.add("implementation", libB)

        val logger = rootProject.logger
        val factory = ProjectMetadataFactory(logger)

        // when
        val metadataMap = factory.buildProjectMetadataMap(rootProject)
        val serviceMetadata = metadataMap[":service"]

        // then
        serviceMetadata shouldNotBe null
        serviceMetadata!!.dependencies shouldHaveSize 2
        val dependencyNames = serviceMetadata.dependencies.map { it.fullyQualifiedName }
        dependencyNames shouldContain ":lib-a"
        dependencyNames shouldContain ":lib-b"
    }

    test("should check if project has specific dependency") {
        // given
        val rootProject = ProjectBuilder.builder().build()
        val commonLib = ProjectBuilder.builder()
            .withParent(rootProject)
            .withName("common-lib")
            .build()
        val userService = ProjectBuilder.builder()
            .withParent(rootProject)
            .withName("user-service")
            .build()
        val adminUi = ProjectBuilder.builder()
            .withParent(rootProject)
            .withName("admin-ui")
            .build()

        commonLib.pluginManager.apply("java-library")
        userService.pluginManager.apply("java-library")
        adminUi.pluginManager.apply("java-library")

        userService.dependencies.add("implementation", commonLib)
        adminUi.dependencies.add("implementation", userService)

        val logger = rootProject.logger
        val factory = ProjectMetadataFactory(logger)

        // when
        val metadataMap = factory.buildProjectMetadataMap(rootProject)
        val adminUiMetadata = metadataMap[":admin-ui"]
        val userServiceMetadata = metadataMap[":user-service"]

        // then
        adminUiMetadata shouldNotBe null
        adminUiMetadata!!.dependencies shouldHaveSize 1
        adminUiMetadata.dependencies[0].name shouldBe "user-service"

        userServiceMetadata shouldNotBe null
        userServiceMetadata!!.dependencies shouldHaveSize 1
        userServiceMetadata.dependencies[0].name shouldBe "common-lib"
    }

    test("should build metadata with empty changed files when no map provided") {
        // given
        val rootProject = ProjectBuilder.builder().build()
        val service = ProjectBuilder.builder()
            .withParent(rootProject)
            .withName("service")
            .build()

        service.pluginManager.apply("java-library")

        val logger = rootProject.logger
        val factory = ProjectMetadataFactory(logger)

        // when
        val metadataMap = factory.buildProjectMetadataMap(rootProject)

        // then
        val serviceMetadata = metadataMap[":service"]
        serviceMetadata shouldNotBe null
        serviceMetadata!!.changedFiles shouldHaveSize 0
        serviceMetadata.hasChanges() shouldBe false
    }

    test("should build metadata with changed files when map provided") {
        // given
        val rootProject = ProjectBuilder.builder().build()
        val service = ProjectBuilder.builder()
            .withParent(rootProject)
            .withName("service")
            .build()

        service.pluginManager.apply("java-library")

        val logger = rootProject.logger
        val factory = ProjectMetadataFactory(logger)

        val changedFilesMap = mapOf(
            ":service" to listOf("src/main/Service.kt", "build.gradle.kts")
        )

        // when
        val metadataMap = factory.buildProjectMetadataMap(rootProject, changedFilesMap)

        // then
        val serviceMetadata = metadataMap[":service"]
        serviceMetadata shouldNotBe null
        serviceMetadata!!.changedFiles shouldHaveSize 2
        serviceMetadata.changedFiles shouldContain "src/main/Service.kt"
        serviceMetadata.changedFiles shouldContain "build.gradle.kts"
        serviceMetadata.hasChanges() shouldBe true
    }

    test("should build metadata with changed files for multiple projects") {
        // given
        val rootProject = ProjectBuilder.builder().build()
        val libA = ProjectBuilder.builder()
            .withParent(rootProject)
            .withName("lib-a")
            .build()
        val libB = ProjectBuilder.builder()
            .withParent(rootProject)
            .withName("lib-b")
            .build()

        libA.pluginManager.apply("java-library")
        libB.pluginManager.apply("java-library")

        val logger = rootProject.logger
        val factory = ProjectMetadataFactory(logger)

        val changedFilesMap = mapOf(
            ":lib-a" to listOf("lib-a/FileA.kt"),
            ":lib-b" to listOf("lib-b/FileB1.kt", "lib-b/FileB2.kt")
        )

        // when
        val metadataMap = factory.buildProjectMetadataMap(rootProject, changedFilesMap)

        // then
        val libAMetadata = metadataMap[":lib-a"]
        libAMetadata shouldNotBe null
        libAMetadata!!.changedFiles shouldHaveSize 1
        libAMetadata.changedFiles shouldContain "lib-a/FileA.kt"
        libAMetadata.hasChanges() shouldBe true

        val libBMetadata = metadataMap[":lib-b"]
        libBMetadata shouldNotBe null
        libBMetadata!!.changedFiles shouldHaveSize 2
        libBMetadata.changedFiles shouldContain "lib-b/FileB1.kt"
        libBMetadata.changedFiles shouldContain "lib-b/FileB2.kt"
        libBMetadata.hasChanges() shouldBe true
    }

    test("should build single project metadata with changed files") {
        // given
        val rootProject = ProjectBuilder.builder().build()
        val service = ProjectBuilder.builder()
            .withParent(rootProject)
            .withName("service")
            .build()

        service.pluginManager.apply("java-library")

        val logger = rootProject.logger
        val factory = ProjectMetadataFactory(logger)

        val changedFilesMap = mapOf(
            ":service" to listOf("src/main/Service.kt", "src/test/ServiceTest.kt")
        )

        // when
        val serviceMetadata = factory.buildProjectMetadata(service, changedFilesMap)

        // then
        serviceMetadata.name shouldBe "service"
        serviceMetadata.fullyQualifiedName shouldBe ":service"
        serviceMetadata.changedFiles shouldHaveSize 2
        serviceMetadata.changedFiles shouldContain "src/main/Service.kt"
        serviceMetadata.changedFiles shouldContain "src/test/ServiceTest.kt"
        serviceMetadata.hasChanges() shouldBe true
    }

    test("should preserve changed files in dependencies") {
        // given
        val rootProject = ProjectBuilder.builder().build()
        val commonLib = ProjectBuilder.builder()
            .withParent(rootProject)
            .withName("common-lib")
            .build()
        val service = ProjectBuilder.builder()
            .withParent(rootProject)
            .withName("service")
            .build()

        commonLib.pluginManager.apply("java-library")
        service.pluginManager.apply("java-library")
        service.dependencies.add("implementation", commonLib)

        val logger = rootProject.logger
        val factory = ProjectMetadataFactory(logger)

        val changedFilesMap = mapOf(
            ":common-lib" to listOf("common-lib/Utils.kt"),
            ":service" to listOf("service/Service.kt")
        )

        // when
        val metadataMap = factory.buildProjectMetadataMap(rootProject, changedFilesMap)

        // then
        val serviceMetadata = metadataMap[":service"]
        serviceMetadata shouldNotBe null
        serviceMetadata!!.changedFiles shouldHaveSize 1
        serviceMetadata.changedFiles shouldContain "service/Service.kt"
        serviceMetadata.hasChanges() shouldBe true

        serviceMetadata.dependencies shouldHaveSize 1
        val commonLibMetadata = serviceMetadata.dependencies[0]
        commonLibMetadata.changedFiles shouldHaveSize 1
        commonLibMetadata.changedFiles shouldContain "common-lib/Utils.kt"
        commonLibMetadata.hasChanges() shouldBe true
    }

    test("should detect platform project dependency without reflection") {
        // given
        val rootProject = ProjectBuilder.builder().build()
        val platform = ProjectBuilder.builder()
            .withParent(rootProject)
            .withName("platform")
            .build()
        val service = ProjectBuilder.builder()
            .withParent(rootProject)
            .withName("service")
            .build()

        platform.pluginManager.apply("java-platform")
        service.pluginManager.apply("java-library")

        service.dependencies.add("implementation", service.dependencies.platform(platform))

        val logger = rootProject.logger
        val factory = ProjectMetadataFactory(logger)

        // when
        val metadataMap = factory.buildProjectMetadataMap(rootProject)

        // then
        val serviceMetadata = metadataMap[":service"]
        serviceMetadata shouldNotBe null
        serviceMetadata!!.dependencies shouldHaveSize 1
        serviceMetadata.dependencies[0].fullyQualifiedName shouldBe ":platform"
    }

    test("should not recurse infinitely on self-referencing dependency") {
        // given: a project that declares a dependency on itself, as java-test-fixtures does
        // via testImplementation(testFixtures(project)) (issue #204)
        val rootProject = ProjectBuilder.builder().build()
        val lib = ProjectBuilder.builder()
            .withParent(rootProject)
            .withName("lib")
            .build()

        lib.pluginManager.apply("java-library")
        lib.dependencies.add("testImplementation", lib)

        val logger = rootProject.logger
        val factory = ProjectMetadataFactory(logger)

        // when
        val metadataMap = factory.buildProjectMetadataMap(rootProject)

        // then: the self-reference is dropped instead of overflowing the stack
        val libMetadata = metadataMap[":lib"]
        libMetadata shouldNotBe null
        libMetadata!!.dependencies shouldHaveSize 0
    }

    test("should not recurse infinitely when java-test-fixtures plugin is applied") {
        // given: java-test-fixtures automatically adds a ProjectDependency on the project itself
        val rootProject = ProjectBuilder.builder().build()
        val lib = ProjectBuilder.builder()
            .withParent(rootProject)
            .withName("lib")
            .build()
        val app = ProjectBuilder.builder()
            .withParent(rootProject)
            .withName("app")
            .build()

        lib.pluginManager.apply("java-library")
        lib.pluginManager.apply("java-test-fixtures")
        app.pluginManager.apply("java-library")
        app.dependencies.add("implementation", lib)

        val logger = rootProject.logger
        val factory = ProjectMetadataFactory(logger)

        // when
        val metadataMap = factory.buildProjectMetadataMap(rootProject)

        // then: real dependencies are preserved, the self-reference is dropped
        val libMetadata = metadataMap[":lib"]
        libMetadata shouldNotBe null
        libMetadata!!.dependencies shouldHaveSize 0

        val appMetadata = metadataMap[":app"]
        appMetadata shouldNotBe null
        appMetadata!!.dependencies shouldHaveSize 1
        appMetadata.dependencies[0].fullyQualifiedName shouldBe ":lib"
    }

    test("should not recurse infinitely on cross-configuration dependency cycle") {
        // given: :lib-a testImplementation-depends on :lib-b while :lib-b
        // implementation-depends on :lib-a — legal in Gradle, but a declared cycle (issue #204)
        val rootProject = ProjectBuilder.builder().build()
        val libA = ProjectBuilder.builder()
            .withParent(rootProject)
            .withName("lib-a")
            .build()
        val libB = ProjectBuilder.builder()
            .withParent(rootProject)
            .withName("lib-b")
            .build()

        libA.pluginManager.apply("java-library")
        libB.pluginManager.apply("java-library")
        libA.dependencies.add("testImplementation", libB)
        libB.dependencies.add("implementation", libA)

        val logger = rootProject.logger
        val factory = ProjectMetadataFactory(logger)

        val changedFilesMap = mapOf(
            ":lib-b" to listOf("lib-b/File.kt")
        )

        // when
        val metadataMap = factory.buildProjectMetadataMap(rootProject, changedFilesMap)

        // then: metadata is built for both projects without overflowing the stack,
        // and change detection still propagates along the retained edge
        val libAMetadata = metadataMap[":lib-a"]
        libAMetadata shouldNotBe null
        libAMetadata!!.dependencies shouldHaveSize 1
        libAMetadata.dependencies[0].fullyQualifiedName shouldBe ":lib-b"
        libAMetadata.hasChanges() shouldBe true

        val libBMetadata = metadataMap[":lib-b"]
        libBMetadata shouldNotBe null
        libBMetadata!!.hasChanges() shouldBe true
    }

    test("should handle projects with no changes in changed files map") {
        // given
        val rootProject = ProjectBuilder.builder().build()
        val serviceA = ProjectBuilder.builder()
            .withParent(rootProject)
            .withName("service-a")
            .build()
        val serviceB = ProjectBuilder.builder()
            .withParent(rootProject)
            .withName("service-b")
            .build()

        serviceA.pluginManager.apply("java-library")
        serviceB.pluginManager.apply("java-library")

        val logger = rootProject.logger
        val factory = ProjectMetadataFactory(logger)

        val changedFilesMap = mapOf(
            ":service-a" to listOf("service-a/File.kt")
            // service-b not in map
        )

        // when
        val metadataMap = factory.buildProjectMetadataMap(rootProject, changedFilesMap)

        // then
        val serviceAMetadata = metadataMap[":service-a"]
        serviceAMetadata shouldNotBe null
        serviceAMetadata!!.changedFiles shouldHaveSize 1
        serviceAMetadata.hasChanges() shouldBe true

        val serviceBMetadata = metadataMap[":service-b"]
        serviceBMetadata shouldNotBe null
        serviceBMetadata!!.changedFiles shouldHaveSize 0
        serviceBMetadata.hasChanges() shouldBe false
    }
})
