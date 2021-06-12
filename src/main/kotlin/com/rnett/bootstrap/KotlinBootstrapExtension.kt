package com.rnett.bootstrap

import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.provider.Provider
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URL
import java.time.DayOfWeek

public val Project.kotlinBootstrapVersion: String? get() = extensions.extraProperties.properties["kotlinBootstrapVersion"]?.toString()

public fun Project.kotlinBootstrapOr(version: String): String {
    return kotlinBootstrapVersion ?: version
}

public fun Settings.kotlinBootstrap(block: KotlinBootstrapExtension.() -> Unit) {
    kotlinBootstrap.apply(block)
}

public val Settings.kotlinBootstrap: KotlinBootstrapExtension get() = extensions.getByName("kotlinBootstrap") as KotlinBootstrapExtension

public sealed class Scheduling(public val cron: String) {
    protected companion object {
        internal val logger = LoggerFactory.getLogger(KotlinBootstrapExtension::class.java)
    }

    protected fun checkMH(minute: Int, hour: Int) {
        require(minute >= 0) { "Can't have minute < 0" }
        require(minute < 60) { "Can't have minute >= 60" }
        require(hour >= 0) { "Can't have minute < 0" }
        require(hour < 23) { "Can't have minute >= 23" }
    }

    public data class Daily(val minute: Int = 0, val hour: Int = 0) : Scheduling("$minute $hour * * *") {
        init {
            checkMH(minute, hour)
        }
    }

    public data class Weekly(val minute: Int = 0, val hour: Int = 0, val dayOfWeek: DayOfWeek = DayOfWeek.SATURDAY) :
        Scheduling("$minute $hour * * ${dayOfWeek.value}") {
        init {
            checkMH(minute, hour)
        }
    }

    public data class Monthly(val minute: Int = 0, val hour: Int = 0, val dayOfMonth: Int = 1) :
        Scheduling("$minute $hour $dayOfMonth * *") {
        init {
            checkMH(minute, hour)
            require(dayOfMonth >= 1) { "Can't have day of month < 1" }
            require(dayOfMonth <= 31) { "Can't have day of month > 31" }
            if (dayOfMonth > 28)
                logger.warn("Day of month is > 28, may not run on all months")
        }
    }
}

public class KotlinBootstrapExtension(
    private val rootProjectDir: File,
    private val useBootstrapProp: Provider<String>
) {
    /**
     * The bootstrap version to use.  Setting to non-`null` will use that version.
     * Will be overridden by the `kotlinBootstrap` property if specified.
     *
     * If the version is`auto`, `latest`, blank or empty, uses latest bootstrap version.
     * Otherwise uses the latest version that matches the version as a regex (fed directly to [Regex]).
     * Both of these are applied after any [filter]s.
     */
    public var bootstrapVersion: String? = null

    /**
     * Setting this to `true` will cause the bootstrap version to never be used,
     * even if the property is set.
     */
    public var disabled: Boolean = false

    /**
     * If `true` (as it is by default), will substitute non-plugin dependencies with groups
     * of `org.jetbrains.kotlin` or sub-groups.
     */
    public var substituteDependencies: Boolean = true

    private val filters = mutableListOf<(String) -> Boolean>()

    private fun matches(number: String) = filters.all { it(number) }

    /**
     * Apply a filter to the bootstrap versions.
     * This is always applied, even when the version is specified via property.
     */
    public fun filter(filter: (String) -> Boolean) {
        filters += filter
    }

    /**
     * Helper to use the latest bootstrap version.  Just sets [bootstrapVersion] to `"latest"`.
     */
    public fun useLatestVersion() {
        bootstrapVersion = "latest"
    }

    private val logger = LoggerFactory.getLogger(KotlinBootstrapPlugin::class.java)

    private fun latestVersions(): List<String> {
        val regex = Regex("number=\"([^\"]+)\"")
        val text =
            URL("https://teamcity.jetbrains.com/guestAuth/app/rest/builds/multiple/buildType:Kotlin_KotlinPublic_BuildNumber,tag:bootstrap,status:Success,state:finished,count:100")
                .readText()
        return regex.findAll(text).map { it.groupValues[1] }.toList()
    }

    internal val bootstrapEnabled by lazy {
        !disabled && (
                bootstrapVersion != null || useBootstrapProp.isPresent
                )
    }

    internal val realBootstrapVersion: String? by lazy {
        if (!bootstrapEnabled)
            return@lazy null

        val prop = (useBootstrapProp.orNull ?: bootstrapVersion ?: return@lazy null)

        logger.debug("Looking up bootstrap versions for version: \"$prop\"")

        val allVersions = latestVersions()

        val versions = allVersions.filter { matches(it) }

        logger.debug("Bootstrap versions matching the filters: $versions")

        fun firstOrError(): String =
            versions.firstOrNull() ?: error("No bootstrap version matching filters.  Versions: $allVersions")

        if (prop.isBlank())
            return@lazy firstOrError()

        return@lazy when (prop.toLowerCase()) {
            "auto" -> firstOrError()
            "latest" -> firstOrError()
            else -> {
                val regex = Regex(prop)
                versions.firstOrNull { regex.matches(it) }
                    ?: error("No bootstrap version matching regex $regex and filters.  Versions: $allVersions")
            }
        }
    }

    public fun generateGithubWorkflow(
        gradleCommand: String = "assemble",
        jdk: String = "15",
        runner: String = "ubuntu-latest",
        scheduling: Scheduling? = Scheduling.Weekly(),
        baseDir: File = rootProjectDir,
        force: Boolean = false
    ) {
        generateGithubWorkflow(listOf("./gradlew $gradleCommand"), jdk, runner, scheduling, baseDir, force)
    }

    public fun generateGithubWorkflow(
        commands: List<String>,
        jdk: String = "15",
        runner: String = "ubuntu-latest",
        scheduling: Scheduling? = Scheduling.Weekly(),
        baseDir: File = rootProjectDir,
        force: Boolean = false
    ) {
        generateCustomGithubWorkflow(
            """
                - name: Compile
                  run: |
                    ${commands.joinToString("\n")}
            """.trimIndent(),
            jdk, runner, scheduling, baseDir, force
        )
    }

    public fun generateCustomGithubWorkflow(
        @Language("yml") steps: String,
        jdk: String = "15",
        runner: String = "ubuntu-latest",
        scheduling: Scheduling? = Scheduling.Weekly(),
        baseDir: File = rootProjectDir,
        force: Boolean = false
    ) {
        val file = File(baseDir, ".github/workflows/kotlin-bootstrap-test.yml")
        if (file.exists()) {
            if (!force)
                return
        } else {
            file.parentFile.mkdirs()
        }

        val sign = "\$"

        @Language("yml")
        val jobs = """
jobs:
  test-no-bootstrap:
    name: Compile normally
    runs-on: $runner
    continue-on-error: true
    steps:
      - uses: actions/checkout@v2
      - name: Set up JDK $jdk
        uses: actions/setup-java@v1
        with:
          java-version: $jdk

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

${steps.replaceIndent("      ")}

  test-kotlin-bootstrap:
    name: Compile with Kotlin bootstrap
    runs-on: $runner
    continue-on-error: true
    needs: test-no-bootstrap
    if: ${sign}{{ needs.publish.test-no-bootstrap.result == 'success' }}
    env:
      ORG_GRADLE_PROJECT_kotlinBootstrap: "latest"
    steps:
      - uses: actions/checkout@v2
      - name: Set up JDK $jdk
        uses: actions/setup-java@v1
        with:
          java-version: $jdk

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

${steps.replaceIndent("      ")}

  check-results:
    name: Results
    needs: [test-no-bootstrap, test-kotlin-bootstrap]
    runs-on: ubuntu-latest
    if: always()
    steps:
      - name: Original Compile Failed
        if: ${sign}{{ needs.publish.test-no-bootstrap.result != 'success' }}
        run: echo "::warning::Compilation without bootstrap failed, aborting"
        
      - name: Bootstrap Compile failed
        if:  ${sign}{{ needs.publish.test-no-bootstrap.result == 'success' && needs.publish.test-kotlin-bootstrap.result != 'success' }}
        run: echo "::error::Compilation with Kotlin bootstrap failed"
        
        """.trimIndent()

        file.writeText(buildString {
            appendLine(
                """
                name: Kotlin Bootstrap Test
                on:
                  workflow_dispatch:
            """.trimIndent()
            )
            if (scheduling != null) {
                appendLine("  schedule:")
                appendLine("    - cron: \"${scheduling.cron}\"")
            }
            append(jobs)
        })
    }
}