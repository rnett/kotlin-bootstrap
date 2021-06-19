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
        gradleCommand: String = "assemble"
    ) {
        bootstrap(listOf("./gradlew $gradleCommand"))
    }

    public fun bootstrap(commands: List<String>) {
        bootstrapCustom(
            """
                - name: Compile
                  run: |
                    ${commands.joinToString("\n")}
            """.trimIndent()
        )
    }

    public fun bootstrapCustom(@Language("yml") steps: String) {
        generateCustomGithubWorkflow(steps, false)
    }

    public fun eap(
        gradleCommand: String = "assemble"
    ) {
        eap(listOf("./gradlew $gradleCommand"))
    }

    public fun eap(commands: List<String>) {
        eapCustom(
            """
                - name: Compile
                  run: |
                    ${commands.joinToString("\n")}
            """.trimIndent()
        )
    }

    public fun eapCustom(@Language("yml") steps: String) {
        generateCustomGithubWorkflow(steps, true)
    }

    public fun both(gradleCommand: String = "assemble") {
        bootstrap(gradleCommand)
        eap(gradleCommand)
    }

    public fun both(commands: List<String>) {
        bootstrap(commands)
        eap(commands)
    }

    public fun bothCustom(@Language("yml") steps: String) {
        bootstrapCustom(steps)
        eapCustom(steps)
    }

    private fun generateCustomGithubWorkflow(
        @Language("yml") steps: String,
        isEap: Boolean
    ) {

        val key = if (isEap) "eap" else "bootstrap"

        val file = File(baseDir, ".github/workflows/kotlin-$key-test.yml")
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
                name: Kotlin ${key.capitalize()} Test
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
            val checkout = if (branch != null) {
                """
      - name: Checkout default branch
        uses: actions/checkout@v2
        
        ${
                    if (scheduling != null) """
      - name: Checkout branch for scheduled
        if: github.event_name == 'schedule'
        uses: actions/checkout@v2
        with:
          ref: $branch
          """ else ""
                }
          
      - name: Checkout branch for manual
        if: github.event_name == 'workflow_dispatch' && github.event.inputs.branch != ''
        uses: actions/checkout@v2
        with:
          ref: ${sign}{{ github.event.inputs.branch }}
                """
            } else {
                """
      - name: Checkout
        uses: actions/checkout@v2
                """
            }

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
        if:  ${sign}{{ needs.test-no-$key.result == 'success' && needs.test-kotlin-$key.result != 'success' }}
        run: echo "::error::Compilation with Kotlin $key failed"
                
                """
            )

        })
    }
}