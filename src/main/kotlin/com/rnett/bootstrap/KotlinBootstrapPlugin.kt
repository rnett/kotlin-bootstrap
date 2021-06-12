package com.rnett.bootstrap

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.provider.Provider
import org.gradle.plugin.use.PluginDependencySpec
import java.net.URI
import java.net.URL

private val latestBootstrapVersion by lazy {
    URL("https://teamcity.jetbrains.com/guestAuth/app/rest/builds/buildType:Kotlin_KotlinPublic_BuildNumber,tag:bootstrap,status:Success,state:finished/number").readText()
}

private lateinit var useBootstrapProp: Provider<String>

private val bootstrapVersion by lazy {
    val prop = useBootstrapProp.orNull ?: return@lazy null
    if(prop.isBlank())
        return@lazy latestBootstrapVersion

    when(prop){
        "auto" -> latestBootstrapVersion
        "latest" -> latestBootstrapVersion
        else -> prop
    }
}

infix fun PluginDependencySpec.bootstrapOrVersion(version: String): PluginDependencySpec {
    return version(bootstrapVersion ?: version)
}

fun bootstrapOr(version: String): String {
    return bootstrapVersion ?: version
}

fun String.orBootstrapVersion() = bootstrapVersion ?: this

class KotlinBootstrapPlugin: Plugin<Settings> {
    override fun apply(settings: Settings) {
        useBootstrapProp = settings.providers.gradleProperty("kotlinBootstrap").forUseAtConfigurationTime()

        settings.gradle.beforeProject {
            if(bootstrapVersion != null){
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
                    if(bootstrapVersion != null) {
                        if (target.id.id.startsWith("org.jetbrains.kotlin.")) {
                            useVersion(bootstrapVersion)
                        }
                    }
                }
            }
            repositories {
                maven {
                    url = URI("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap")
                }
            }
        }
    }
}