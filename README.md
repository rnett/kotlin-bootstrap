# Kotlin Future Testing

[![Maven Central](https://img.shields.io/maven-central/v/com.github.rnett.kotlin-future-testing/kotlin-future-testing)](https://search.maven.org/artifact/com.github.rnett.kotlin-future-testing/kotlin-future-testing)
[![Sonatype Nexus (Snapshots)](https://img.shields.io/nexus/s/com.github.rnett.kotlin-future-testing/kotlin-future-testing?server=https%3A%2F%2Foss.sonatype.org)](https://oss.sonatype.org/content/repositories/snapshots/com/github/rnett/kotlin-future-testing/)
[![GitHub Repo](https://img.shields.io/badge/GitHub-kotlin--future--testing-blue?logo=github)](https://github.com/rnett/kotlin-future-testing)
[![License](https://img.shields.io/badge/License-Apache%202.0-yellowgreen.svg)](https://opensource.org/licenses/Apache-2.0)

[comment]: <> ([![Changelog]&#40;https://img.shields.io/badge/Changelog-CHANGELOG.md-green&#41;]&#40;./CHANGELOG.md#changelog&#41;)

A Gradle **settings** plugin to easily test your code with the latest Kotlin bootstrap or EAP version.

Plugin: `com.github.rnett.kotlin-future-testing`

Will not work if you are using multiple Kotlin versions.

## Usage

Apply the plugin to your settings file.

The plugin can be configured in the `kotlinFutureTesting` block. For an overview of what can be configured,
see [the extension's docs](https://rnett.github.io/kotlin-future-testing/release/-kotlin%20-future%20-testing/com.rnett.future.testing/-kotlin-future-testing-extension/index.html)
.

To use a bootstrap or eap version, either call `forceBootstrap()` or `forceEap()` in the extension configuration, or
pass the `kotlinBootstrap` or `kotlinEap` gradle properties to the build. Note that the properties can be passed with no
value to use the latest version, i.e. `-PkotlinBootstrap`.

If the value of the passed version property is blank, `auto`, or `latest` the latest version (matching filters and
clamping)
will be used. Otherwise, the value will be used as the value if it passes the configured clamping.

### Using the future version

If a future version is successfully found, all plugins with ids starting with `org.jetbrains.kotlin.` will have their
versions set to the future version. The bootstrap repository will be added if necessary.

If `substituteDependencies` (in the extension) is true, any dependencies with the group `org.jetbrains.kotlin` or a
subgroup will also have their versions set to the future versions. The bootstrap repository will be added if necessary.

Regardless of whether a future version is found or not, all projects will have the extra property
of `kotlinFutureVersion` set to the current `KotlinFutureTestingVersion`. This can be accessed through
the `kotlinFutureVersion` accessor. It provides information on the current Kotlin version, and utilities for selecting
different versions (or anything)
depending on whether a future version is being used or not.
See [its docs](https://rnett.github.io/kotlin-future-testing/release/-kotlin%20-future%20-testing/com.rnett.future.testing/-kotlin-future-testing-version/index.html)
.

## ICE Reporting

In addition to using future versions, the plugin provides support for reporting Internal Compiler Errors. If enabled,
reports will be made in `$rootDir/build/kotlin-future-testing-ICE-report` when an internal compiler error happens.

Whether reports are made is controlled by the extension's `reportICEs` property, which has three states:

* `Always` - always make a report
* `IfProperty` - if the `reportICEs` gradle property is present and not equal to `false`.
* `Never` - never make a report.

It is `Always` by default.

These reports are made to provide enough information to reproduce the ICE if the project is provided or in a public git
repo, **without directly providing any code**. Note that code can still sometimes end up in the compiler error
stacktraces, which are reported, so if you have sensitive code you will want to manually check or disable these reports.

Each report contains:

* the root project name
* the failing task's path
* the task's input properties
* the Kotlin version used, and it's kind
* the stderr of the failing task, usually just the exception's stacktrace

Additionally, if the plugin can find a git root by walking up the directory tree, the following will be added:

* the path to the root project from the git root
* the current git ref
* the git remotes

And if the build is being ran in Github actions, the URL of the workflow run report will be added.

You can see the schema
yourself [here](https://github.com/rnett/kotlin-future-testing/blob/main/src/main/kotlin/com/rnett/future/testing/ice/IceListener.kt#L35)
.

Example reports are [here](./example-ice-reports), generated from the [test](./test) project.

For now, this only applies to things reported as internal compiler errors, i.e. not linker errors.

## GitHub workflow generation

An experimental
function [`generateGithubWorkflows`](https://rnett.github.io/kotlin-future-testing/release/-kotlin%20-future%20-testing/com.rnett.future.testing/-kotlin-future-testing-extension/generate-github-workflows.html)
is provided in the extension to generate GitHub workflows to run tests with bootstrap and/or eap versions, on command
and/or a schedule. It is capable of generating workflows with custom steps, a set branch, and multiple runners, but
makes generating the most common setups easy. ICE reports will be uploaded as an artifact unless disabled (
the `reportICEs` property is set). The workflow will be manually trigger-able, and can be passed a schedule (the default
is weekly).

Note that the generation is experimental, so you should manually inspect the generated workflows. They make good
starting points if you have a complicated setup.

Also note that generation is done immediately when generation is called, so if `force` is true it will be re-generated
every time the gradle project is loaded.

