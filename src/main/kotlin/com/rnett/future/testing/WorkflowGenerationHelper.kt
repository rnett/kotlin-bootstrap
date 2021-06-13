package com.rnett.future.testing

import org.intellij.lang.annotations.Language
import java.io.File

public class GithubWorkflowGenerator(
    private val jdk: String,
    private val runner: String,
    private val scheduling: Scheduling?,
    private val baseDir: File,
    private val force: Boolean,
    private val reportICEs: Boolean
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
            appendLine(
                """
                name: Kotlin ${key.capitalize()} Test
                on:
                  workflow_dispatch:
            """.trimIndent()
            )
            if (scheduling != null) {
                appendLine("  schedule:")
                appendLine("    - cron: \"${scheduling.cron}\"")
            }

            // language=yml
            append(
                """
jobs:
  test-no-$key:
    name: Compile normally
    runs-on: ${runner}
    continue-on-error: true
    steps:
      - uses: actions/checkout@v2
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
    continue-on-error: true
    ${if (!reportICEs) "needs: test-no-$key" else ""}
    ${if (!reportICEs) "if: ${sign}{{ needs.test-no-$key.result == 'success' }}" else ""}
    outputs:
      was-ice: ${sign}{{ steps.was-ice.files_exists }}
    env:
      ORG_GRADLE_PROJECT_kotlin${key.capitalize()}: "latest"
      ${if (reportICEs) "ORG_GRADLE_PROJECT_reportICEs: \"true\"" else ""}
    steps:
      - uses: actions/checkout@v2
      - name: Set up JDK ${jdk}
        uses: actions/setup-java@v1
        with:
          java-version: ${jdk}

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

${steps.replaceIndent("      ")}
"""
            )

            // language=yml
            if (reportICEs) {
                append(
                    """

      - name: Check for ICE report
        if: ${sign}{{ failure() }}
        id: was-ice
        uses: andstor/file-existence-action@v1
        with:
          files: ".kotlin-future-testing-ICE-report"

      - name: Archive ICE report
        uses: actions/upload-artifact@v2
        if: ${sign}{{ failure() && steps.was-ice.files_exists == 'true' }}
        with:
          name: future-ice-report
          path: .kotlin-future-testing-ICE-report
"""
                )
            }

            // language=yml
            append(
                """
  check-results:
    name: Results
    needs: [test-no-$key, test-kotlin-$key]
    runs-on: ubuntu-latest
    if: always()
    steps:
      - name: Original Compile Failed
        if: ${sign}{{ needs.test-no-$key.result != 'success' }}
        run: echo "::warning::Compilation without $key failed, aborting"
        
      - name: ${key.capitalize()} Compile failed
        if:  ${sign}{{ needs.test-no-$key.result == 'success' && needs.test-kotlin-$key.result != 'success' }}
        run: echo "::error::Compilation with Kotlin $key failed"
                
                """
            )
            // language=yml
            if (reportICEs) {
                append(
                    """
      - name: Download ICE report
        if: ${sign}{{ needs.test-kotlin$key.was-ice }}
        uses: actions/download-artifact@v2
        with:
          name: future-ice-report
      - name: Save git info
        if: ${sign}{{ needs.test-kotlin$key.was-ice }}
        run: |
          'echo "Workflow: ${sign}{{ github.repository }}/${sign}{{ github.workflow }}#${sign}{{ github.run_number }}" >> .kotlin-future-testing-run-info'
          'echo "Git Ref: ${sign}{{ github.ref }}, SHA: ${sign}{{ github.sha }}" >> .kotlin-future-testing-run-info'
          'echo "Run ID: ${sign}{{ github.run_id }}" >> .kotlin-future-testing-run-info'
      
      - name: Archive ICE report
        uses: actions/upload-artifact@v2
        if: ${sign}{{ needs.test-kotlin$key.was-ice }}
        with:
          name: future-ice-report
          path: |
            .kotlin-future-testing-ICE-report
            .kotlin-future-testing-run-info
                """
                )
            }

        })
    }
}