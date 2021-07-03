package com.rnett.future.testing.ice

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

internal class IceListener(private val outputDir: File, doReportProp: Provider<String>) :
    TaskExecutionListener {
    private val stderr = mutableMapOf<Task, StderrListener>()

    private val doReport by lazy { doReportProp.orNull != null && doReportProp.orNull?.toLowerCase() != "false" }

    private class StderrListener() : StandardOutputListener {
        val builder: StringBuilder = java.lang.StringBuilder()
        override fun onOutput(output: CharSequence?) {
            output?.let { builder.append(output) }
        }
    }

    @Serializable
    class Report(
        val rootProjectName: String,
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
        if (exception != null &&
            "Internal compiler error" in exception.cause?.message.orEmpty()
        ) {
            @Suppress("UNCHECKED_CAST")
            val report = Report(
                task.project.rootProject.name,
                task.path,
                task.inputs.properties.mapValues { (it.value as Any?).toString() },
                task.project.kotlinFutureVersion.version,
                task.project.kotlinFutureVersion.versionKind.name,
                stderr,
                gitRef(task.project.projectDir),
                gitRemotes(task.project.projectDir),
                GithubEnv.runUrl
            )
            val fileName = "${task.project.rootProject.name}:${task.path.trim(':')}".replace(":", "$")
            outputDir.mkdirs()
            File(outputDir, "$fileName.json").writeText(json.encodeToString(report))
            File(outputDir, "$fileName.txt").writeText(report.humanReadable())
        }
    }
}