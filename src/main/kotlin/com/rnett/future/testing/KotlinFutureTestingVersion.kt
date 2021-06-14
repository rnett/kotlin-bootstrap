package com.rnett.future.testing

import org.gradle.api.Project

public enum class KotlinVersionKind {
    Bootstrap, Eap, Release;
}

internal const val kotlinFutureVersionProp = "kotlinFutureVersion"

public val Project.kotlinFutureVersion: KotlinFutureTestingVersion
    get() = (extensions.extraProperties.properties[kotlinFutureVersionProp] as Lazy<*>?)?.value as KotlinFutureTestingVersion?
        ?: error("No Kotlin future version extension found")


public data class KotlinFutureTestingVersion(
    public val versionKind: KotlinVersionKind,
    public val version: String,
) {

    public fun or(normal: String): String = if (isFuture) this.version else normal
    public operator fun invoke(normal: String): String = or(normal)

    public fun <T> select(future: T, normal: T): T = if (isFuture) future else normal
    public operator fun <T> invoke(future: T, normal: T): T = select(future, normal)

    public fun <T> select(futureNormal: Pair<T, T>): T = select(futureNormal.first, futureNormal.second)
    public operator fun <T> invoke(futureNormal: Pair<T, T>): T = select(futureNormal)

    public val isEap: Boolean get() = versionKind == KotlinVersionKind.Eap
    public val isBootstrap: Boolean get() = versionKind == KotlinVersionKind.Bootstrap
    public val isFuture: Boolean get() = versionKind != KotlinVersionKind.Release
    public val isRelease: Boolean get() = versionKind == KotlinVersionKind.Release
}