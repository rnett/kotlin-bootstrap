package com.rnett.future.testing.ice

import com.rnett.future.testing.ReportICEs
import com.rnett.future.testing.kotlinFutureVersion
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.logging.StandardOutputListener
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskState
import java.io.File


internal class IceListener(
    private val outputDir: File,
    doReportProp: Provider<String>,
    doReport: () -> ReportICEs
) :
    TaskExecutionListener {
    private val stderr = mutableMapOf<Task, StderrListener>()

    private val doReport by lazy {
        when (doReport()) {
            ReportICEs.Always -> true
            ReportICEs.IfProperty -> (doReportProp.orNull != null && doReportProp.orNull?.toLowerCase() != "false")
            ReportICEs.Never -> false
        }
    }

    private class StderrListener() : StandardOutputListener {
        val builder: StringBuilder = java.lang.StringBuilder()
        override fun onOutput(output: CharSequence?) {
            output?.let { builder.append(output) }
        }
    }

    @Serializable
    class Report(
        val rootProjectName: String,
        val rootProjectPathFromGitRoot: String?,
        val taskPath: String,
        val taskInputs: Map<String, String>,
        val kotlinVersion: String,
        val kotlinVersionKind: String,
        val compilerStderr: String,
        val gitRef: String?,
        val gitRemotes: Map<String, String>?,
        val githubRunUrl: String?,
    ) {
        fun humanReadable(): String = buildString {
            appendLine("Root Project: $rootProjectName")
            rootProjectPathFromGitRoot?.let {
                appendLine("Root Project path from Git root: $it")
            }
            appendLine("Task: $taskPath")
            appendLine("Task input properties: $taskInputs")
            appendLine("Kotlin version: $kotlinVersion")
            appendLine("Kotlin version kind: $kotlinVersionKind")
            gitRef?.let { appendLine("Git ref: $it") }
            gitRemotes?.let { appendLine("Git remotes: $it") }
            githubRunUrl?.let { appendLine("Github run url: $it") }
            appendLine(compilerStderr)
        }
    }

    companion object {
        private val json = Json {
            prettyPrint = true
        }

        private val icePackages = setOf(
            "org.jetbrains.kotlin.backend",
            "org.jetbrains.kotlin.ir",
            "org.jetbrains.kotlin.cli",
            "org.jetbrains.kotlin.daemon",
        )

        private fun isICE(cause: Throwable, stderr: String, taskName: String): Boolean {
            val taskNameLC = taskName.toLowerCase()

            if ("test" in taskNameLC || "check" in taskNameLC) return false

            if ("Internal compiler error" in cause.message.orEmpty()) return true
            if (icePackages.any { "at $it" in stderr }) return true
            if (".konan" in stderr) return true
            if ("error: Linking" in stderr || "ld: " in stderr) return true
            return false
        }

    }

    override fun beforeExecute(task: Task) {
        if (!doReport) return
        val listener = stderr.getOrPut(task) { StderrListener() }
        task.logging.addStandardErrorListener(listener)
    }

    override fun afterExecute(task: Task, state: TaskState) {
        if (!doReport) return

        val stderr = stderr.remove(task)?.also {
            task.logging.removeStandardErrorListener(it)
        }?.builder?.toString() ?: ""

        val exception = state.failure
        //TODO report linker errors too
        val cause = exception?.cause ?: return
        if (exception != null && isICE(cause, stderr, task.name)) {
            val gitDir = gitDir(task.project.rootDir)
            val rootRelPath = gitDir?.let {
                task.project.rootDir.relativeTo(it.parentFile).path
            }

            @Suppress("UNCHECKED_CAST")
            val report = Report(
                task.project.rootProject.name,
                rootRelPath,
                task.path,
                task.inputs.properties.mapValues { (it.value as Any?).toString() },
                task.project.kotlinFutureVersion.version,
                task.project.kotlinFutureVersion.versionKind.name,
                stderr,
                gitRef(gitDir),
                gitRemotes(gitDir),
                GithubEnv.runUrl
            )
            val fileName = "${task.project.rootProject.name}:${task.path.trim(':')}".replace(":", "$")
            outputDir.mkdirs()
            File(outputDir, "$fileName.json").writeText(json.encodeToString(report))
            File(outputDir, "$fileName.txt").writeText(report.humanReadable())
        }
    }
}