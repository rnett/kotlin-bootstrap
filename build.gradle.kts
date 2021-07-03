import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URL

plugins {
    kotlin("jvm") version "1.5.10"
    kotlin("plugin.serialization") version "1.5.10"
    id("com.vanniktech.maven.publish") version "0.15.1"
    id("org.jetbrains.dokka") version "1.4.32"
    `java-gradle-plugin`
    `kotlin-dsl`
}

version = "0.1.0-RC"
group = "com.github.rnett.kotlin-future-testing"
description = "A Gradle settings plugin to use Kotlin future versions"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.2.1")
}

kotlin {
    target {
        attributes {
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 8)
        }
        explicitApi()
    }
    sourceSets.all {
        languageSettings {
            useExperimentalAnnotation("kotlin.contracts.ExperimentalContracts")
            useExperimentalAnnotation("kotlin.RequiresOptIn")
        }
    }
}

tasks.test {
    useJUnit()
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}

gradlePlugin {
    plugins {
        create("kotlinFutureTestingPlugin") {
            id = "com.github.rnett.kotlin-future-testing"
            displayName = "Kotlin Future Testing Plugin"
            description = "A plugin for testing future versions of Kotlin"
            implementationClass = "com.rnett.future.testing.KotlinFutureTestingPlugin"
        }
    }
}

afterEvaluate {
    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "org.gradle.maven-publish")
    apply(plugin = "com.vanniktech.maven.publish")
    val project = this

    extensions.getByType<com.vanniktech.maven.publish.MavenPublishBaseExtension>().apply {
        if (!version.toString().toLowerCase().endsWith("snapshot")) {
            val stagingProfileId = project.findProperty("sonatypeRepositoryId")?.toString()
            publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.DEFAULT, stagingProfileId)
        }

        pom {
            name.set("Kotlin Future Testing Gradle Plugin")
            description.set(project.description)
            inceptionYear.set("2021")
            url.set("https://github.com/rnett/kotlin-future-testing/")

            licenses {
                license {
                    name.set("The Apache Software License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    distribution.set("repo")
                }
            }

            scm {
                url.set("https://github.com/rnett/kotlin-future-testing.git")
                connection.set("scm:git:git://github.com/rnett/kotlin-future-testing.git")
                developerConnection.set("scm:git:ssh://git@github.com/rnett/kotlin-future-testing.git")
            }

            developers {
                developer {
                    id.set("rnett")
                    name.set("Ryan Nett")
                    url.set("https://github.com/rnett/")
                }
            }
        }
    }
}

val sourceLinkBranch: String by project

tasks.withType<org.jetbrains.dokka.gradle.AbstractDokkaTask>() {

    val dokkaSourceSets = when (this) {
        is org.jetbrains.dokka.gradle.DokkaTask -> dokkaSourceSets
        is org.jetbrains.dokka.gradle.DokkaTaskPartial -> dokkaSourceSets
        else -> return@withType
    }

    moduleName.set("Kotlin Future Testing")
    moduleVersion.set(version.toString())

    dokkaSourceSets.configureEach {
        includes.from(file("docs.md"))
        includeNonPublic.set(false)
        suppressObviousFunctions.set(true)
        suppressInheritedMembers.set(true)
        skipDeprecated.set(false)
        skipEmptyPackages.set(true)
        jdkVersion.set(8)

        val sourceSet = this.sourceSetID.sourceSetName

        sourceLink {
            localDirectory.set(file("src/main/kotlin"))

            remoteUrl.set(URL("https://github.com/rnett/kotlin-future-testing/blob/$sourceLinkBranch/src/$sourceSet/kotlin"))
            remoteLineSuffix.set("#L")
        }
    }
}

val header = "Kotlin Future Testing"

tasks.create<Copy>("generateReadme") {
    from("README.md")
    into(buildDir)
    filter {
        it.replace(
            "# $header",
            "# [$header](https://github.com/rnett/kotlin-future-testing)"
        )
    }
}