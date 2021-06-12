# Kotlin Bootstrap Plugin

[![Maven Central](https://img.shields.io/maven-central/v/com.github.rnett.kotlin-bootstrap/kotlin-bootstrap)](https://search.maven.org/artifact/com.github.rnett.kotlin-bootstrap/kotlin-bootstrap)
[![Sonatype Nexus (Snapshots)](https://img.shields.io/nexus/s/com.github.rnett.kotlin-bootstrap/kotlin-bootstrap?server=https%3A%2F%2Foss.sonatype.org)](https://oss.sonatype.org/content/repositories/snapshots/com/github/rnett/kotlin-bootstrap/)

A **settings** plugin to easily test your code with the latest Kotlin bootstrap compiler.

Plugin: `com.github.rnett.kotlin-bootstrap`

## Usage

Apply the plugin to your settings file.

The bootstrap version can be set using the property `kotlinBootstrap`, or in a settings file
with `kotlinBootstrap.bootstrapVersion`. The property takes precedence if it is present.

If the version is null or `kotlinBootstrap.disabled` is set to `true` in the settings file, doesn't use a bootstrap
version.

If the version is non-null, gets all bootstrap builds, filters them by any filters set w/ `kotlinBootstrap.filter()` (in
the settings file), and then tries to find a match for the version. A version of `auto`, `latest`, or a blank or empty
string matches everything, otherwise tries to match the version as a regex (feeds the string directly to `Regex()`).

When a bootstrap version is used, redirects all plugins with ids starting with `org.jetbrains.kotlin.` to the bootstrap
version and adds the bootstrap repository for plugins and dependencies.  **Note that project dependencies are not
substituted**, you should rely on the Kotlin plugin version either implicitly or with `getKotlinPluginVersion()`
or `kotlin.coreLibrariesVersion`.

