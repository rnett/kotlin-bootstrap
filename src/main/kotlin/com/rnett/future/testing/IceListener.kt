package com.rnett.future.testing

import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.logging.StandardOutputListener
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskState
import java.io.File

internal class IceListener(val outputFile: File? = null, forUseAtConfigurationTime: Provider<String>) :
    TaskExecutionListener {
    private val stderr = mutableMapOf<Task, StderrListener>()

    private val doReport by lazy { forUseAtConfigurationTime.orNull != null && forUseAtConfigurationTime.orNull?.toLowerCase() != "false" }

    init {
        if (outputFile != null) {
            if (outputFile.exists())
                outputFile.delete()
            else
                outputFile.parentFile.mkdirs()
        }
    }

    private class StderrListener() : StandardOutputListener {
        val builder: StringBuilder = java.lang.StringBuilder()
        override fun onOutput(output: CharSequence?) {
            output?.let { builder.append(output) }
        }

    }

    override fun beforeExecute(task: Task) {
        val listener = stderr.getOrPut(task) { StderrListener() }
        task.logging.addStandardErrorListener(listener)
    }

    override fun afterExecute(task: Task, state: TaskState) {
        val stderr = stderr.remove(task)?.also {
            task.logging.removeStandardErrorListener(it)
        }?.builder?.toString() ?: ""

        if (state.failure != null && (stderr.startsWith("e:") || stderr.startsWith("f:")) &&
            stderr.contains("at org.jetbrains.kotlin.") &&
            doReport
        ) {
            task.inputs
            outputFile?.appendText(buildString {
                appendLine("Task ${task.path}")
                appendLine("Input properties: ${task.inputs.properties}")
                append(stderr)
                append("\n\n\n")
            })
        }
    }
}