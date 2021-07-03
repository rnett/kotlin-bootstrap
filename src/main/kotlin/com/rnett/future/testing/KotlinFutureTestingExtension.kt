package com.rnett.future.testing

import org.gradle.api.initialization.Settings
import org.gradle.api.provider.Provider
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URL

internal const val kotlinFutureTestingExtension = "kotlinFutureTesting"

/**
 * Configure the kotlin future testing extension.
 */
public fun Settings.kotlinFutureTesting(block: KotlinFutureTestingExtension.() -> Unit) {
    kotlinFutureTesting.apply(block)
}

/**
 * Get the kotlin future testing extension
 */
public val Settings.kotlinFutureTesting: KotlinFutureTestingExtension
    get() =
        extensions.getByName(kotlinFutureTestingExtension) as KotlinFutureTestingExtension

internal enum class VersionClamping {
    None, Feature, Incremental;

    companion object {
        private val incrementalRegex = Regex("(\\d+.\\d+.\\d)")
        private val featureRegex = Regex("(\\d+.\\d+)")
    }

    internal fun matchingHelper(
        original: String,
        kind: KotlinVersionKind,
        versions: List<String>
    ): Pair<String, String?> {
        val prefix = when (this) {
            None -> return "" to versions.firstOrNull() ?: error("No Kotlin ${kind.name.toLowerCase()} versions found")
            Feature -> featureRegex.find(original)?.groupValues?.get(1)
                ?: error("Can't find feature version of $original")
            Incremental -> incrementalRegex.find(original)?.groupValues?.get(1)
                ?: error("Can't find incremental version of $original")
        }
        return prefix to versions.firstOrNull { it.startsWith(prefix) }
    }

    internal fun matchingOrNull(original: String, kind: KotlinVersionKind, versions: List<String>): String? =
        matchingHelper(original, kind, versions).second

    internal fun matching(original: String, kind: KotlinVersionKind, versions: List<String>): String {
        val (prefix, result) = matchingHelper(original, kind, versions)
        return result
            ?: error("No Kotlin ${kind.name.toLowerCase()} versions found with the same ${this.name.toLowerCase()} version as $original ($prefix).  Versions: $versions")
    }
}

internal sealed class KotlinFutureVersionProp(val versionKind: KotlinVersionKind) {
    abstract val version: String?

    val isEap: Boolean get() = this is Eap
    val isBootstrap: Boolean get() = this is Bootstrap
    val isFuture: Boolean get() = this !is None

    object None : KotlinFutureVersionProp(KotlinVersionKind.Unchanged) {
        override val version: String? = null
        override fun toString(): String = "None"
    }

    data class Eap(override val version: String) : KotlinFutureVersionProp(KotlinVersionKind.Eap)
    data class Bootstrap(override val version: String) : KotlinFutureVersionProp(KotlinVersionKind.Bootstrap)
}

/**
 * A context for version filters, providing the original version.
 */
public class FilterContext(public val originalVersion: String)

/**
 * A future version filter.
 */
public typealias VersionFilter = FilterContext.(version: String) -> Boolean

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

    private val bootstrapFilters = mutableListOf<VersionFilter>()
    private val eapFilters = mutableListOf<VersionFilter>()

    /**
     * Apply a filter to the bootstrap versions.
     * This is always applied, even when the version is specified via property.
     */
    public fun filter(filter: VersionFilter) {
        bootstrapFilters += filter
        eapFilters += filter
    }

    /**
     * Apply a filter to the bootstrap versions.
     * This is always applied, even when the version is specified via property.
     */
    public fun filterBootstrap(filter: VersionFilter) {
        bootstrapFilters += filter
    }

    /**
     * Apply a filter to the bootstrap versions.
     * This is always applied, even when the version is specified via property.
     */
    public fun filterEap(filter: VersionFilter) {
        eapFilters += filter
    }

    /**
     * Require a future version with the same feature version (i.e. 1.5) as the original version.
     */
    public fun requireSameFeatureVersion() {
        clamping = VersionClamping.Feature
    }

    /**
     * Require a future version with the same incremental version (i.e. 1.5.10) as the original version.
     */
    public fun requireSameIncrementalVersion() {
        clamping = VersionClamping.Incremental
    }

    /**
     * Prefer a future version with the same feature version (i.e. 1.5) as the original version.
     */
    public fun preferSameFeatureVersion() {
        preferredClamping = VersionClamping.Feature
    }

    /**
     * Prefer a future version with the same incremental version (i.e. 1.5.10) as the original version.
     */
    public fun preferSameIncrementalVersion() {
        preferredClamping = VersionClamping.Incremental
    }

    private var forceProp: KotlinFutureVersionProp? = null

    /**
     * Equivalent to setting the `kotlinBootstrap` property, but overrides any property values.
     *
     * Useful for fixing issues.
     *
     * Does not override [disabled].
     *
     * Note that this only applies to the current build, so you will need to add it for any including builds as we..
     */
    public fun forceBootstrap(version: String = "latest") {
        forceProp = KotlinFutureVersionProp.Bootstrap(version)
    }

    /**
     * Equivalent to setting the `kotlinEap` property, but overrides any property values.
     *
     * Useful for fixing issues.
     *
     * Does not override [disabled].
     *
     * Note that this only applies to the current build, so you will need to add it for any including builds as we..
     */
    public fun forceEap(version: String = "latest") {
        forceProp = KotlinFutureVersionProp.Eap(version)
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

    internal val isBootstrap: Boolean by lazy {
        futureProp().isBootstrap
    }

    internal val isFuture: Boolean by lazy {
        futureProp().isFuture
    }

    private fun futureProp(): KotlinFutureVersionProp {
        if (disabled)
            return KotlinFutureVersionProp.None

        forceProp?.let { return it }

        bootstrapProp.orNull?.let {
            return KotlinFutureVersionProp.Bootstrap(it)
        }
        eapProp.orNull?.let {
            return KotlinFutureVersionProp.Eap(it)
        }
        return KotlinFutureVersionProp.None
    }

    private fun List<String>.matching(filters: List<VersionFilter>): List<String> {
        logger.debug("Checking filters on versions $this")
        val context = FilterContext(oldKotlinVersion())
        return filter { str -> filters.all { context.it(str) } }.ifEmpty { error("No version matching filters.  Versions: $this") }
    }

    private var preferredClamping: VersionClamping? = null
    private var clamping = VersionClamping.None

    internal var oldKotlinVersion: String? = null

    private fun oldKotlinVersion() =
        oldKotlinVersion ?: error("No Kotlin version found, did you use any Kotlin plugins?")

    internal val version by lazy {
        val prop = futureProp()
        val version = KotlinFutureTestingVersion(
            prop.versionKind,
            futureVersion(prop),
            oldKotlinVersion()
        )
        if (version.isFuture) {
            if (version.version == oldKotlinVersion()) {
                println("\nUsing configured kotlin version of ${oldKotlinVersion()}, no future versions found.\n")
                return@lazy KotlinFutureTestingVersion(
                    KotlinVersionKind.Unchanged,
                    oldKotlinVersion(),
                    oldKotlinVersion()
                )
            } else {
                println("\nUsing future version of Kotlin: ${version.version}, type is ${version.versionKind}.\n")
            }
        }
        version
    }

    private fun futureVersion(prop: KotlinFutureVersionProp): String {
        val oldVersion = oldKotlinVersion()
        if (!isFuture)
            return oldVersion

        val futureVersions = futureVersions(prop)

        val preferred = preferredClamping?.matchingOrNull(oldVersion, prop.versionKind, futureVersions)

        return preferred ?: clamping.matching(oldVersion, prop.versionKind, futureVersions)
    }

    private fun futureVersions(prop: KotlinFutureVersionProp): List<String> {
        if (disabled)
            return emptyList()

        if (eapProp.isPresent && bootstrapProp.isPresent)
            logger.warn("Both Kotlin EAP and bootstrap versions are configured, using bootstrap")

        if (prop is KotlinFutureVersionProp.None)
            return emptyList()

        logger.debug("Looking up Kotlin future versions for version: \"$prop\"")

        val version = prop.version!!

        val isLatest = version.isBlank() || version.toLowerCase().let { it == "auto" || it == "latest" }

        if (!isLatest) {
            logger.info("Kotlin future version is exact, using $prop")
            return listOf(prop.version!!)
        }

        logger.debug("Looking up latest version")

        return when (prop) {
            is KotlinFutureVersionProp.Bootstrap -> latestBootstrapVersions().matching(bootstrapFilters)
            is KotlinFutureVersionProp.Eap -> latestEapVersions().matching(eapFilters)
            KotlinFutureVersionProp.None -> emptyList()
        }.filter { it > oldKotlinVersion() && !it.startsWith(oldKotlinVersion()) }.let {
            if (it.isEmpty()) {
                logger.warn("No future versions found for kind ${prop.versionKind}, using current version $oldKotlinVersion()")
                listOf(oldKotlinVersion())
            } else {
                logger.info("Found Kotlin future versions $it")
                it
            }
        }
    }

    /**
     * Configure GitHub workflow generation
     *
     * @param jdk the JDK version to use
     * @param runner the GitHub Actions runner OSs to use.  Multiple OSs will be done in a matrix.  Must have at least one element.
     * @param scheduling how often to schedule runs, or `null` to not schedule any
     * @param baseDir the git root, workflows will be generated in `$baseDir/.github/workflows`.
     * @param branch the branch to use for scheduled runs
     * @param force whether to overwrite existing workflows of the same name (`kotlin-${bootstrap|eap}-test.yml`)
     */
    @ExperimentalGithubWorkflowGeneration
    public inline fun generateGithubWorkflows(
        jdk: String = "15",
        runners: List<String> = listOf("ubuntu-latest"),
        scheduling: Scheduling? = Scheduling.Weekly(),
        baseDir: File = rootProjectDir,
        branch: String? = null,
        force: Boolean = false,
        block: GithubWorkflowGenerator.() -> Unit
    ) {
        GithubWorkflowGenerator(jdk, runners, scheduling, baseDir, branch, force).apply(block)
    }
}