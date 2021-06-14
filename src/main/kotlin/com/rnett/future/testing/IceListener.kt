package com.rnett.future.testing

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

    companion object {
        private fun writeReport(
            baseDir: File,
            rootProjectName: String,
            taskPath: String,
            taskInputs: Map<String, Any>,
            kotlinVersion: String,
            kotlinVersionKind: KotlinVersionKind,
            gitRef: String?,
            gitRemotes: Map<String, String>?,
            stderr: String
        ) {
            val fullTaskPath = "$rootProjectName:${taskPath.trim(':')}"
            val file = File(baseDir, fullTaskPath.replace(":", "-"))
            file.parentFile.mkdirs()
            file.writeText(buildString {
                appendLine("Root Project: $rootProjectName")
                appendLine("Task: $taskPath")
                appendLine("Task input properties: $taskInputs")
                appendLine("Kotlin version: $kotlinVersion")
                appendLine("Kotlin version kind: $kotlinVersionKind")
                appendLine("Git ref: $gitRef")
                appendLine("Git remotes: $gitRemotes")
                appendLine(stderr)
            })
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
            writeReport(
                outputDir,
                task.project.rootProject.name,
                task.path,
                task.inputs.properties,
                task.project.kotlinFutureVersion.version,
                task.project.kotlinFutureVersion.versionKind,
                gitRef(task.project.projectDir),
                gitRemotes(task.project.projectDir),
                stderr
            )
        }
    }
}