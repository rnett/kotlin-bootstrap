package com.rnett.future.testing

import java.io.File
import kotlin.reflect.KProperty

internal object GithubEnv {

    private class GithubEnvDelegate(val name: String?) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): String =
            System.getenv("GITHUB_" + (name?.toUpperCase() ?: property.name.toUpperCase()))
    }

    private fun delegate(name: String? = null) = GithubEnvDelegate(name)
    val workflow by delegate()
    val runId by delegate("run_id")
    val repository by delegate()
    val sha by delegate()
    val ref by delegate()

    val runUrl
        get() = if (isGithub)
            "https://github.com/$repository/actions/runs/$runId"
        else
            null

    val isGithub get() = System.getenv("GITHUB_WORKFLOW") != null
}

private fun gitDir(start: File): File? {
    var root = start.canonicalFile
    while (!File(root, ".git").exists()) {
        val parent = root.parentFile ?: return null
        root = parent
    }

    return File(root, ".git")
}

private val remoteRegex = Regex("\\[remote \"([^\"]+)\"\\]\n\\s+url = ([^\n]+)\n", RegexOption.MULTILINE)

//TODO make ICE reports in gradle, w/ git info.  Can send from here easier.
//  then accumulate + make artifact
//   I would like to get the remote for this commit, not sure I can if it's floating
internal fun gitRemotes(root: File): Map<String, String>? {
    if (GithubEnv.isGithub) {
        return mapOf("origin" to "https://github.com/${GithubEnv.repository}.git")
    } else {
        val gitDir = gitDir(root) ?: return null
        val config = File(gitDir, "config").readText()
        return remoteRegex.findAll(config).associate { it.groupValues[1] to it.groupValues[2] }
    }
}

internal fun gitRef(root: File): String? {
    if (GithubEnv.isGithub) {
        return GithubEnv.sha + " " + GithubEnv.ref
    } else {
        val gitDir = gitDir(root) ?: return null
        val head = File(gitDir, "HEAD").readText()

        return if (head.startsWith("ref: ")) {
            val ref = head.removePrefix("ref: ").trim()
            val sha = File(gitDir, ref).readText().trim()
            "$sha $ref"
        } else {
            head
        }
    }
}

public fun main() {
    File("./test>file>task").writeText("test")
}