import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URL

plugins {
    kotlin("jvm") version "1.4.31"
    kotlin("plugin.serialization") version "1.4.31"
    id("com.vanniktech.maven.publish") version "0.16.0"
    id("org.jetbrains.dokka") version "1.4.32"
    id("com.github.johnrengelman.shadow") version "7.0.0"
    `java-gradle-plugin`
    `kotlin-dsl`
}

version = "0.1.1-SNAPSHOT"
group = "com.github.rnett.kotlin-future-testing"
description = "A Gradle settings plugin to use Kotlin future versions"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.1.0")
}

kotlin {
    target {
        attributes {
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 8)
        }
        explicitApi()
        mavenPublication {
            project.shadow.component(this)
        }
    }
    sourceSets.all {
        languageSettings.apply {
            useExperimentalAnnotation("kotlin.contracts.ExperimentalContracts")
            useExperimentalAnnotation("kotlin.RequiresOptIn")
        }
    }
}

val shadowJar = tasks.shadowJar.apply {
    configure {
        archiveClassifier.set("")
        relocate("kotlinx.serialization", "com.rnett.future.testing.kotlinx.serialization")
        dependencies {
            exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib.*:.*"))
        }
    }
}

tasks.jar.configure {
    finalizedBy(shadowJar)
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

        sourceLink {
            localDirectory.set(file("src/main/kotlin"))

            val sourceLink =
                "https://github.com/rnett/kotlin-future-testing/blob/$sourceLinkBranch/src/main/kotlin"

            println("Source link: $sourceLink")

            remoteUrl.set(URL(sourceLink))
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