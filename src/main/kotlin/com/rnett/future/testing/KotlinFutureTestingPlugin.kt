package com.rnett.future.testing

import com.rnett.future.testing.ice.IceListener
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI

/**
 * The settings plugin
 */
public class KotlinFutureTestingPlugin : Plugin<Settings> {
    private val logger = LoggerFactory.getLogger(KotlinFutureTestingPlugin::class.java)

    override fun apply(settings: Settings) {
        val extension = KotlinFutureTestingExtension(
            settings.rootDir,
            settings.providers.gradleProperty(Constants.bootstrapProperty).forUseAtConfigurationTime(),
            settings.providers.gradleProperty(Constants.eapProperty).forUseAtConfigurationTime()
        )

        settings.gradle.settingsEvaluated {
            if (extension.isFuture) {
                settings.gradle.taskGraph.addTaskExecutionListener(
                    IceListener(
                        File(settings.rootDir, "build/${Constants.iceReportDir}"),
                        settings.providers.gradleProperty(Constants.reportIceProperty).forUseAtConfigurationTime(),
                        extension::alwaysReportICEs
                    )
                )
            }
        }


        settings.extensions.add(kotlinFutureTestingExtension, extension)

        settings.gradle.beforeProject {
            extensions.extraProperties[kotlinFutureVersionProp] = lazy { extension.version }

            if (extension.substituteDependencies) {
                configurations.configureEach {
                    resolutionStrategy {
                        eachDependency {
                            if (extension.isFuture) {
                                if (target.group == Constants.kotlinGroup || target.group.startsWith("${Constants.kotlinGroup}.")) {
                                    val version = extension.version.version

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
                        url = URI(Constants.bootstrapRepo)
                    }
                }
            }
        }

        settings.gradle.settingsEvaluated {
            pluginManagement {
                resolutionStrategy {
                    eachPlugin {
                        if (target.id.id.startsWith("${Constants.kotlinGroup}.")) {
                            val oldVersion = target.version
                            if (oldVersion != null && (extension.oldKotlinVersion == null || oldVersion > extension.oldKotlinVersion!!)) {
                                logger.info("Found old kotlin version $oldVersion")
                                extension.oldKotlinVersion = oldVersion
                            }

                            if (extension.isFuture) {
                                val version = extension.version.version
                                logger.info("Using bootstrap version $version for plugin ${target.id.id}")
                                useVersion(version)
                            }
                        }
                    }
                }
                if (extension.isBootstrap) {
                    repositories.apply {
                        maven {
                            url = URI(Constants.bootstrapRepo)
                        }
                    }
                }
            }
        }
    }
}