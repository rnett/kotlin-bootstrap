package com.rnett.future.testing

import org.intellij.lang.annotations.Language
import java.io.File

//TODO branch for non-manual (two checkout actions, one w/ branch one w/o?  or manually check out if non?)
public class GithubWorkflowGenerator(
    private val jdk: String,
    private val runner: String,
    private val scheduling: Scheduling?,
    private val baseDir: File,
    private val branch: String?,
    private val force: Boolean
) {

    public fun bootstrap(
        gradleCommand: String = "assemble",
        suffix: String = ""
    ) {
        bootstrapCommands("./gradlew $gradleCommand", suffix = suffix)
    }

    public fun bootstrapCommands(
        vararg commands: String,
        suffix: String = ""
    ) {
        bootstrapCustom(
            """
                - name: Compile
                  run: |
                    ${commands.joinToString("\n")}
            """.trimIndent(),
            suffix
        )
    }

    public fun bootstrapCustom(
        @Language("yml") steps: String,
        suffix: String = ""
    ) {
        generateCustomGithubWorkflow(steps, false, suffix)
    }

    public fun eap(
        gradleCommand: String = "assemble",
        suffix: String = ""
    ) {
        eapCommands("./gradlew $gradleCommand", suffix = suffix)
    }

    public fun eapCommands(
        vararg commands: String,
        suffix: String = ""
    ) {
        eapCustom(
            """
                - name: Compile
                  run: |
                    ${commands.joinToString("\n")}
            """.trimIndent(),
            suffix
        )
    }

    public fun eapCustom(
        @Language("yml") steps: String,
        suffix: String = ""
    ) {
        generateCustomGithubWorkflow(steps, true, suffix)
    }

    public fun both(
        gradleCommand: String = "assemble",
        suffix: String = ""
    ) {
        bootstrap(gradleCommand, suffix)
        eap(gradleCommand, suffix)
    }

    public fun both(
        vararg commands: String,
        suffix: String = ""
    ) {
        bootstrapCommands(*commands, suffix = suffix)
        eapCommands(*commands, suffix = suffix)
    }

    public fun bothCustom(
        @Language("yml") steps: String,
        suffix: String = ""
    ) {
        bootstrapCustom(steps, suffix)
        eapCustom(steps, suffix)
    }

    private fun generateCustomGithubWorkflow(
        @Language("yml") steps: String,
        isEap: Boolean,
        suffix: String
    ) {

        val key = if (isEap) "eap" else "bootstrap"

        val fileName = "kotlin-$key-test".let {
            if (suffix.isNotBlank())
                "$it-$suffix"
            else
                it
        }

        val file = File(baseDir, ".github/workflows/$fileName.yml")
        if (file.exists()) {
            if (!force)
                return
        } else {
            file.parentFile.mkdirs()
        }

        val sign = "\$"

        file.writeText(buildString {
            // language=yml
            appendLine(
                """
                name: Kotlin ${key.capitalize()} Test ${suffix.capitalize()}
                on:
                  workflow_dispatch:
                    inputs:
                      branch:
                        description: "Target branch"
                        required: false
                        default: '${branch ?: ""}'
            """.trimIndent()
            )
            if (scheduling != null) {
                appendLine("  schedule:")
                appendLine("    - cron: \"${scheduling.cron}\"")
            }

            // language=yml
            val checkout = """
      - name: Checkout default branch
        uses: actions/checkout@v2
          
      - name: Checkout target branch for manual
        if: github.event_name == 'workflow_dispatch' && github.event.inputs.branch != ''
        uses: actions/checkout@v2
        with:
          ref: ${sign}{{ github.event.inputs.branch }}
        
        ${
                if (scheduling != null) """
      - name: Checkout target branch for scheduled
        if: github.event_name == 'schedule'
        uses: actions/checkout@v2
        with:
          ref: $branch
""" else ""
            }
                """

            // language=yml
            append(
                """
jobs:
  test-no-$key:
    name: Compile normally
    runs-on: ${runner}
    steps:
$checkout
        
      - name: Set up JDK ${jdk}
        uses: actions/setup-java@v1
        with:
          java-version: ${jdk}

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

${steps.replaceIndent("      ")}

  test-kotlin-$key:
    name: Compile with Kotlin $key
    runs-on: ${runner}
    outputs:
      was-ice: ${sign}{{ steps.was-ice.outputs.files_exists }}
    env:
      ORG_GRADLE_PROJECT_kotlin${key.capitalize()}: "latest"
      ORG_GRADLE_PROJECT_reportICEs: "true"
    steps:
$checkout

      - name: Set up JDK ${jdk}
        uses: actions/setup-java@v1
        with:
          java-version: ${jdk}

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

${steps.replaceIndent("      ")}

      - name: Archive ICE report
        uses: actions/upload-artifact@v2
        if: ${sign}{{ failure() }}
        with:
          name: kotlin-future-ICE-report
          path: build/kotlin-future-testing-ICE-report
        
  check-results:
    name: Results
    needs: [test-no-$key, test-kotlin-$key]
    runs-on: ubuntu-latest
    if: always()
    steps:
      - name: Original Compile Failed
        if: ${sign}{{ needs.test-no-$key.result != 'success' }}
        run: echo "::warning::Compilation without $key failed, aborting"
        
      - name: Only ${key.capitalize()} Compile failed
        if: ${sign}{{ needs.test-no-$key.result == 'success' && needs.test-kotlin-$key.result != 'success' }}
        run: echo "::error::Compilation with Kotlin $key failed"
                
                """
            )

        })
    }
}