package com.rnett.future.testing

import org.intellij.lang.annotations.Language
import java.io.File

internal object WorkflowGenerationHelper {

    fun generateCustomGithubWorkflow(
        @Language("yml") steps: String,
        jdk: String,
        runner: String,
        scheduling: Scheduling?,
        baseDir: File,
        force: Boolean,
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

        @Language("yml")
        val jobs = """
jobs:
  test-no-$key:
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

  test-kotlin-$key:
    name: Compile with Kotlin $key
    runs-on: $runner
    continue-on-error: true
    needs: test-no-$key
    if: ${sign}{{ needs.test-no-$key.result == 'success' }}
    env:
      ORG_GRADLE_PROJECT_kotlin${key.capitalize()}: "latest"
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
        
        """.trimIndent()

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
            append(jobs)
        })
    }
}