package com.rnett.future.testing.github

import com.rnett.future.testing.Constants
import org.intellij.lang.annotations.Language
import java.io.File

/**
 * Experimental marker for Github workflow generation.  The generated workflows should be checked,
 * and the generation APIs may change.
 */
@RequiresOptIn(
    "Experimental Github workflow generation, check it manually.  APIs may change.",
    level = RequiresOptIn.Level.WARNING
)
@MustBeDocumented
public annotation class ExperimentalGithubWorkflowGeneration

@ExperimentalGithubWorkflowGeneration
public class GithubWorkflowGenerator(
    private val jdk: String,
    private val runners: List<String>,
    private val scheduling: Scheduling?,
    private val baseDir: File,
    private val branch: String?,
    private val force: Boolean
) {

    private val commonSteps = mutableListOf<String>()

    init {
        require(runners.isNotEmpty()) { "Must specify at least one runner OS" }
    }

    /**
     * Add some common `run: ` commands to all later generated workflows.
     */
    @ExperimentalGithubWorkflowGeneration
    public fun commonCommands(vararg commands: String, name: String = "Common Setup", id: String? = null) {
        commonStep(buildString {
            appendLine("- name: $name")
            if (id != null)
                append("  id: $id")
            appendLine("  run: |")
            commands.forEach {
                appendLine("    $it")
            }
        })
    }

    /**
     * Add some common step(s) to all later generated workflows.
     */
    @ExperimentalGithubWorkflowGeneration
    public fun commonStep(@Language("yml") steps: String) {
        commonSteps += steps
    }

    /**
     * Generate a bootstrap testing workflow that runs the gradle command line [gradleCommand] (`./gradlew $gradleCommand`).
     *
     * [suffix] will be used to name the workflow, and the workflow file will be named `kotlin-bootstrap-$suffix.yml`.
     */
    @ExperimentalGithubWorkflowGeneration
    public fun bootstrap(
        gradleCommand: String = "assemble",
        suffix: String = "compile"
    ) {
        bootstrapCommands("./gradlew $gradleCommand", suffix = suffix)
    }

    private fun stepFor(commands: Array<out String>): String = """
                - name: Compile
                  run: |
${commands.joinToString("\n").replaceIndent("                    ")}""".trimIndent()

    /**
     * Generate a bootstrap testing workflow that runs [commands] (`run: | $commands`, one per line).
     *
     * [suffix] will be used to name the workflow, and the workflow file will be named `kotlin-bootstrap-$suffix.yml`.
     */
    @ExperimentalGithubWorkflowGeneration
    public fun bootstrapCommands(
        vararg commands: String,
        suffix: String = "compile"
    ) {
        bootstrapCustom(
            stepFor(commands),
            suffix
        )
    }

    /**
     * Generate a bootstrap testing workflow with the steps [steps].
     *
     * Checkout, Gradle, Java, and Kotlin future version setup will be done beforehand.
     *
     * [suffix] will be used to name the workflow, and the workflow file will be named `kotlin-bootstrap-$suffix.yml`.
     */
    @ExperimentalGithubWorkflowGeneration
    public fun bootstrapCustom(
        @Language("yml") steps: String,
        suffix: String = "compile"
    ) {
        generateCustomGithubWorkflow(steps, false, suffix)
    }

    /**
     * Generate an EAP testing workflow that runs the gradle command line [gradleCommand] (`./gradlew $gradleCommand`).
     *
     * [suffix] will be used to name the workflow, and the workflow file will be named `kotlin-eap-$suffix.yml`.
     */
    @ExperimentalGithubWorkflowGeneration
    public fun eap(
        gradleCommand: String = "assemble",
        suffix: String = "compile"
    ) {
        eapCommands("./gradlew $gradleCommand", suffix = suffix)
    }

    /**
     * Generate an EAP testing workflow that runs [commands] (`run: | $commands`, one per line).
     *
     * [suffix] will be used to name the workflow, and the workflow file will be named `kotlin-eap-$suffix.yml`.
     */
    @ExperimentalGithubWorkflowGeneration
    public fun eapCommands(
        vararg commands: String,
        suffix: String = "compile"
    ) {
        eapCustom(
            stepFor(commands),
            suffix
        )
    }

    /**
     * Generate an EAP testing workflow with the steps [steps].
     *
     * Checkout, Gradle, Java, and Kotlin future version setup will be done beforehand.
     *
     * [suffix] will be used to name the workflow, and the workflow file will be named `kotlin-eap-$suffix.yml`.
     */
    @ExperimentalGithubWorkflowGeneration
    public fun eapCustom(
        @Language("yml") steps: String,
        suffix: String = "compile"
    ) {
        generateCustomGithubWorkflow(steps, true, suffix)
    }

    /**
     * Generate bootstrap and EAP testing workflows that run the gradle command line [gradleCommand] (`./gradlew $gradleCommand`).
     *
     * [suffix] will be used to name the workflows, and the workflow files will be named `kotlin-{bootstrap|eap}-$suffix.yml`.
     * @see bootstrap
     * @see eap
     */
    @ExperimentalGithubWorkflowGeneration
    public fun both(
        gradleCommand: String = "assemble",
        suffix: String = "compile"
    ) {
        bootstrap(gradleCommand, suffix)
        eap(gradleCommand, suffix)
    }

    /**
     * Generate bootstrap and EAP testing workflows that run [commands] (`run: | $commands`, one per line).
     *
     * [suffix] will be used to name the workflows, and the workflow files will be named `kotlin-{bootstrap|eap}-$suffix.yml`.
     * @see bootstrapCommands
     * @see eapCommands
     */
    @ExperimentalGithubWorkflowGeneration
    public fun bothCommands(
        vararg commands: String,
        suffix: String = "compile"
    ) {
        bootstrapCommands(*commands, suffix = suffix)
        eapCommands(*commands, suffix = suffix)
    }

    /**
     * Generate bootstrap and EAP testing workflows with the steps [steps].
     *
     * Checkout, Gradle, Java, and Kotlin future version setup will be done beforehand.
     *
     * [suffix] will be used to name the workflows, and the workflow files will be named `kotlin-{bootstrap|eap}-$suffix.yml`.
     * @see bootstrapCustom
     * @see eapCustom
     */
    @ExperimentalGithubWorkflowGeneration
    public fun bothCustom(
        @Language("yml") steps: String,
        suffix: String = "compile"
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

        val fileName = "kotlin-$key".let {
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

        val keyName = key.capitalize()
        val futureProp = if (isEap) Constants.eapProperty else Constants.bootstrapProperty
        val suffixName = suffix.capitalize()

        file.writeText(buildString {
            // language=yml
            appendLine(
                """
                name: Kotlin $keyName $suffixName
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
            val runner = if (runners.size == 1)
                "runs-on: ${runners[0]}"
            else
                """
            strategy:
              matrix:
                os: [ ${runners.joinToString(",  ")} ]
              fail-fast: false
            runs-on: ${sign}{{ matrix.os }}
        """.trimIndent()

            val iceArtifactName = if (runners.size == 1) {
                "kotlin-future-ICE-report"
            } else {
                "kotlin-future-ICE-report-\${{ matrix.os }}"
            }

            // language=yml
            append(
                """
jobs:
  try-no-$key:
    name: $suffixName normally
${runner.replaceIndent("    ")}
    steps:
$checkout
        
      - name: Set up JDK ${jdk}
        uses: actions/setup-java@v1
        with:
          java-version: ${jdk}

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

${commonSteps.joinToString("\n\n").replaceIndent("      ")}

${steps.replaceIndent("      ")}

  try-kotlin-$key:
    name: $suffixName with Kotlin $key
${runner.replaceIndent("    ")}
    env:
      ORG_GRADLE_PROJECT_$futureProp: "latest"
      ORG_GRADLE_PROJECT_${Constants.reportIceProperty}: "true"
    steps:
$checkout

      - name: Set up JDK ${jdk}
        uses: actions/setup-java@v1
        with:
          java-version: ${jdk}

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

${commonSteps.joinToString("\n\n").replaceIndent("      ")}

${steps.replaceIndent("      ")}

      - name: Archive ICE report
        uses: actions/upload-artifact@v2
        if: ${sign}{{ failure() }}
        with:
          name: $iceArtifactName
          path: build/kotlin-future-testing-ICE-report
        
  check-results:
    name: Results
    needs: [try-no-$key, try-kotlin-$key]
    runs-on: ubuntu-latest
    if: always()
    steps:
      - name: Original $suffixName Failed
        if: ${sign}{{ needs.try-no-$key.result != 'success' }}
        run: echo "::warning::Compilation without $key failed, aborting"
        
      - name: Only $keyName $suffixName failed
        if: ${sign}{{ needs.try-no-$key.result == 'success' && needs.try-kotlin-$key.result != 'success' }}
        run: echo "::error::$suffixName with Kotlin $key failed"
                
                """
            )

        })
    }
}