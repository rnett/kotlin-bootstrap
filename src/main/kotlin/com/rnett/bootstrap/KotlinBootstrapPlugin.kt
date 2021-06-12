package com.rnett.bootstrap

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.slf4j.LoggerFactory
import java.net.URI


public class KotlinBootstrapPlugin : Plugin<Settings> {
    private val logger = LoggerFactory.getLogger(KotlinBootstrapPlugin::class.java)

    override fun apply(settings: Settings) {
        val extension = KotlinBootstrapExtension(
            settings.rootDir,
            settings.providers.gradleProperty("kotlinBootstrap").forUseAtConfigurationTime()
        )

        settings.extensions.add("kotlinBootstrap", extension)

        settings.gradle.beforeProject {
            extensions.extraProperties["kotlinBootstrapVersion"] = extension.realBootstrapVersion

            if (extension.substituteDependencies) {
                configurations.configureEach {
                    resolutionStrategy {
                        eachDependency {
                            if (extension.bootstrapEnabled) {
                                if (target.group == "org.jetbrains.kotlin" || target.group.startsWith("org.jetbrains.kotlin.")) {
                                    val version = extension.realBootstrapVersion
                                        ?: error("Could not find a valid bootstrap version")

                                    logger.info("Using bootstrap version $version for dependency $target")

                                    useVersion(version)
                                    because("Using Kotlin bootstrap version $version")
                                }
                            }
                        }
                    }
                }
            }

            if (extension.bootstrapEnabled) {
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
                    if (extension.bootstrapEnabled) {
                        if (target.id.id.startsWith("org.jetbrains.kotlin.")) {
                            val version = extension.realBootstrapVersion
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