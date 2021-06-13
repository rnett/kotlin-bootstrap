package com.rnett.future.testing

import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.provider.Provider
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URL

internal const val kotlinFutureVersionProp = "kotlinFutureVersion"

public val Project.kotlinFutureVersion: KotlinFutureVersion
    get() = extensions.extraProperties.properties[kotlinFutureVersionProp] as KotlinFutureVersion?
        ?: KotlinFutureVersion.None

internal const val kotlinFutureTestingExtension = "kotlinFutureTesting"

public fun Settings.kotlinFutureTesting(block: KotlinFutureTestingExtension.() -> Unit) {
    kotlinFutureTesting.apply(block)
}

public val Settings.kotlinFutureTesting: KotlinFutureTestingExtension
    get() =
        extensions.getByName(kotlinFutureTestingExtension) as KotlinFutureTestingExtension

public sealed class KotlinFutureVersion {
    public abstract val version: String?

    public fun or(normal: String): String = this.version ?: normal
    public operator fun invoke(normal: String): String = or(normal)

    public fun <T> select(future: T, normal: T): T = if (isFuture) future else normal
    public operator fun <T> invoke(future: T, normal: T): T = select(future, normal)

    public fun <T> select(futureNormal: Pair<T, T>): T = select(futureNormal.first, futureNormal.second)
    public operator fun <T> invoke(futureNormal: Pair<T, T>): T = select(futureNormal)

    public val isEap: Boolean get() = this is Eap
    public val isBootstrap: Boolean get() = this is Bootstrap
    public val isFuture: Boolean get() = this !is None

    public object None : KotlinFutureVersion() {
        override val version: String? = null
        override fun toString(): String = "None"
    }

    public data class Eap(override val version: String) : KotlinFutureVersion()
    public data class Bootstrap(override val version: String) : KotlinFutureVersion()
}

public class KotlinFutureTestingExtension(
    @PublishedApi internal val rootProjectDir: File,
    private val bootstrapProp: Provider<String>,
    private val eapProp: Provider<String>
) {
    /**
     * Setting this to `true` will cause the bootstrap version to never be used,
     * even if the property is set.
     */
    public var disabled: Boolean = false

    /**
     * If `true` (as it is by default), will substitute non-plugin dependencies with groups
     * of `org.jetbrains.kotlin` or sub-groups.
     */
    public var substituteDependencies: Boolean = true

    public var reportICEs: Boolean = true

    private val bootstrapFilters = mutableListOf<(String) -> Boolean>()
    private val eapFilters = mutableListOf<(String) -> Boolean>()

    /**
     * Apply a filter to the bootstrap versions.
     * This is always applied, even when the version is specified via property.
     */
    public fun filter(filter: (String) -> Boolean) {
        bootstrapFilters += filter
        eapFilters += filter
    }

    /**
     * Apply a filter to the bootstrap versions.
     * This is always applied, even when the version is specified via property.
     */
    public fun filterBootstrap(filter: (String) -> Boolean) {
        bootstrapFilters += filter
    }


    /**
     * Apply a filter to the bootstrap versions.
     * This is always applied, even when the version is specified via property.
     */
    public fun filterEap(filter: (String) -> Boolean) {
        eapFilters += filter
    }

    private val logger = LoggerFactory.getLogger(KotlinFutureTestingPlugin::class.java)

    private fun latestBootstrapVersions(): List<String> {
        val regex = Regex("number=\"([^\"]+)\"")
        val text =
            URL("https://teamcity.jetbrains.com/guestAuth/app/rest/builds/multiple/buildType:Kotlin_KotlinPublic_BuildNumber,tag:bootstrap,status:Success,state:finished,count:100?fields=build(number)")
                .readText()
        return regex.findAll(text).map { it.groupValues[1] }.toList()
            .also { if (it.isEmpty()) error("No Kotlin bootstrap versions found") }
    }

    private fun latestEapVersions(): List<String> {
        val text = URL("https://api.github.com/repos/jetbrains/kotlin/releases?per_page=6").readText()
        val regex = Regex("\"tag_name\"\\s*:\\s*\"v([^\"]+)\"")
        return regex.findAll(text).map { it.groupValues[1] }.toList()
            .also { if (it.isEmpty()) error("No Kotlin EAP versions found") }
    }

    public val isEap: Boolean by lazy {
        futureProp().isEap
    }

    public val isBootstrap: Boolean by lazy {
        futureProp().isBootstrap
    }

    public val isFuture: Boolean by lazy {
        futureProp().isFuture
    }

    private fun futureProp(): KotlinFutureVersion {
        if (disabled)
            return KotlinFutureVersion.None
        bootstrapProp.orNull?.let {
            return KotlinFutureVersion.Bootstrap(it)
        }
        eapProp.orNull?.let {
            return KotlinFutureVersion.Eap(it)
        }
        return KotlinFutureVersion.None
    }

    private fun List<String>.firstMatching(filters: List<(String) -> Boolean>): String {
        logger.debug("Checking filters on versions $this")
        return firstOrNull { str -> filters.all { it(str) } } ?: error("No version matching filters.  Versions: $this")
    }

    internal val futureVersion: KotlinFutureVersion by lazy {

        if (disabled)
            return@lazy KotlinFutureVersion.None

        if (eapProp.isPresent && bootstrapProp.isPresent)
            logger.warn("Both Kotlin EAP and bootstrap versions are configured, using bootstrap")

        val prop = futureProp()
        if (prop is KotlinFutureVersion.None)
            return@lazy KotlinFutureVersion.None

        logger.debug("Looking up Kotlin future versions for version: \"$prop\"")

        val version = prop.version!!

        val isLatest = version.isBlank() || version.toLowerCase().let { it == "auto" || it == "latest" }

        if (!isLatest) {
            logger.info("Kotlin future version is exact, using $prop")
            return@lazy prop
        }

        logger.debug("Looking up latest version")

        when (prop) {
            is KotlinFutureVersion.Bootstrap -> KotlinFutureVersion.Bootstrap(
                latestBootstrapVersions().firstMatching(bootstrapFilters)
            )
            is KotlinFutureVersion.Eap -> KotlinFutureVersion.Eap(
                latestEapVersions().firstMatching(eapFilters)
            )
            KotlinFutureVersion.None -> KotlinFutureVersion.None
        }.also {
            logger.info("Found Kotlin future version $it")
        }
    }

    public inline fun generateGithubWorkflows(
        jdk: String = "15",
        runner: String = "ubuntu-latest",
        scheduling: Scheduling? = Scheduling.Weekly(),
        baseDir: File = rootProjectDir,
        force: Boolean = false,
        block: GithubWorkflowGenerator.() -> Unit
    ) {
        GithubWorkflowGenerator(jdk, runner, scheduling, baseDir, force, reportICEs).apply(block)
    }
}