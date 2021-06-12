package com.rnett.bootstrap

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.provider.Provider
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URL

private object KotlinBootstrapVersion {
    private val logger = LoggerFactory.getLogger(KotlinBootstrapPlugin::class.java)

    val latestVersionList by lazy {
        val regex = Regex("number=\"([^\"]+)\"")
        val text =
            URL("https://teamcity.jetbrains.com/guestAuth/app/rest/builds/multiple/buildType:Kotlin_KotlinPublic_BuildNumber,tag:bootstrap,status:Success,state:finished,count:100")
                .readText()
        regex.findAll(text).map { it.groupValues[1] }.toList()
    }

    lateinit var useBootstrapProp: Provider<String>

    val bootstrapEnabled
        get() = !KotlinBootstrapExtensionImpl.disabled && (
                KotlinBootstrapExtensionImpl.bootstrapVersion != null || useBootstrapProp.isPresent
                )

    val realBootstrapVersion: String?
        get() {
            if (!bootstrapEnabled)
                return null

            val prop = (useBootstrapProp.orNull ?: KotlinBootstrapExtensionImpl.bootstrapVersion ?: return null)

            logger.debug("Looking up bootstrap versions for version: \"$prop\"")

            val versions = KotlinBootstrapExtensionImpl.matchingVersions

            logger.debug("Bootstrap versions matching the filters: $versions")

            fun firstOrError(): String =
                versions.firstOrNull() ?: error("No bootstrap version matching filters.  Versions: $latestVersionList")

            if (prop.isBlank())
                return firstOrError()

            return when (prop.toLowerCase()) {
                "auto" -> firstOrError()
                "latest" -> firstOrError()
                else -> {
                    val regex = Regex(prop)
                    return versions.firstOrNull { regex.matches(it) }
                        ?: error("No bootstrap version matching regex $regex and filters.  Versions: $latestVersionList")
                }
            }
        }
}

fun bootstrapOr(version: String): String {
    return KotlinBootstrapVersion.realBootstrapVersion ?: version
}

fun String.orBootstrapVersion() = KotlinBootstrapVersion.realBootstrapVersion ?: this

interface KotlinBootstrapExtension {
    var bootstrapVersion: String?
    var disabled: Boolean

    fun filter(filter: (String) -> Boolean)

    fun useLatestVersion() {
        bootstrapVersion = "latest"
    }
}

private object KotlinBootstrapExtensionImpl : KotlinBootstrapExtension {
    override var bootstrapVersion: String? = null
    override var disabled: Boolean = false

    private val filters = mutableListOf<(String) -> Boolean>()

    private fun matches(number: String) = filters.all { it(number) }

    val matchingVersions by lazy { KotlinBootstrapVersion.latestVersionList.filter { matches(it) } }

    override fun filter(filter: (String) -> Boolean) {
        filters += filter
    }
}

fun Settings.kotlinBootstrap(block: KotlinBootstrapExtension.() -> Unit) {
    KotlinBootstrapExtensionImpl.apply(block)
}

val kotlinBootstrap: KotlinBootstrapExtension = KotlinBootstrapExtensionImpl


class KotlinBootstrapPlugin : Plugin<Settings> {
    private val logger = LoggerFactory.getLogger(KotlinBootstrapPlugin::class.java)

    override fun apply(settings: Settings) {
        KotlinBootstrapVersion.useBootstrapProp =
            settings.providers.gradleProperty("kotlinBootstrap").forUseAtConfigurationTime()

        settings.gradle.beforeProject {
            if (KotlinBootstrapVersion.bootstrapEnabled) {
                logger.info("Using bootstrap version, adding boostrap repo for project ${this.path}")
                this.repositories.apply {
                    maven {
                        url = URI("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap")
                    }
                }
            }
        }

        settings.pluginManagement {
            resolutionStrategy {
                eachPlugin {
                    if (KotlinBootstrapVersion.bootstrapEnabled) {
                        if (target.id.id.startsWith("org.jetbrains.kotlin.")) {
                            val version = KotlinBootstrapVersion.realBootstrapVersion
                                ?: error("Could not find a valid bootstrap version")
                            logger.info("Using bootstrap version $version for plugin ${target.id.id}")
                            useVersion(version)
                        }
                    }
                }
            }
            repositories.apply {
                maven {
                    url = URI("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap")
                }
            }
        }
    }
}