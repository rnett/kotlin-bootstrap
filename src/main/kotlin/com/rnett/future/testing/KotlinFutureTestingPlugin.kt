package com.rnett.future.testing

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.slf4j.LoggerFactory
import java.net.URI


public class KotlinFutureTestingPlugin : Plugin<Settings> {
    private val logger = LoggerFactory.getLogger(KotlinFutureTestingPlugin::class.java)

    override fun apply(settings: Settings) {
        val extension = KotlinFutureTestingExtension(
            settings.rootDir,
            settings.providers.gradleProperty("kotlinBootstrap").forUseAtConfigurationTime(),
            settings.providers.gradleProperty("kotlinEap").forUseAtConfigurationTime()
        )

        settings.extensions.add(kotlinFutureTestingExtension, extension)

        settings.gradle.beforeProject {
            extensions.extraProperties[kotlinFutureVersionProp] = extension.futureVersion

            if (extension.substituteDependencies) {
                configurations.configureEach {
                    resolutionStrategy {
                        eachDependency {
                            if (extension.isFuture) {
                                if (target.group == "org.jetbrains.kotlin" || target.group.startsWith("org.jetbrains.kotlin.")) {
                                    val version = extension.futureVersion.version!!

                                    logger.info("Using future version $version for dependency $target")

                                    useVersion(version)
                                    because("Using Kotlin future version $version")
                                }
                            }
                        }
                    }
                }
            }

            if (extension.isBootstrap) {
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
                    if (extension.isBootstrap) {
                        if (target.id.id.startsWith("org.jetbrains.kotlin.")) {
                            val version = extension.futureVersion.version!!
                            logger.info("Using bootstrap version $version for plugin ${target.id.id}")
                            useVersion(version)
                        }
                    }
                }
            }
            if (extension.isBootstrap) {
                repositories.apply {
                    maven {
                        url = URI("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap")
                    }
                }
            }
        }
    }
}