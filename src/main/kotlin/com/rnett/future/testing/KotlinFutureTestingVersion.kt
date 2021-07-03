package com.rnett.future.testing

import org.gradle.api.Project

/**
 * The kind of kotlin version.
 */
public enum class KotlinVersionKind {
    /**
     * A bootstrap version.  Relatively stable, used to compile the compiler.
     */
    Bootstrap,

    /**
     * A released EAP version.
     */
    Eap,

    /**
     * The same version you configured manually.
     */
    Unchanged;
}

internal const val kotlinFutureVersionProp = "kotlinFutureVersion"

/**
 * Get the Kotlin version being used, with its kind.  Will always be set, even if a future version is not used
 * (in that case, the kind will be [KotlinVersionKind.Unchanged] and [KotlinFutureTestingVersion.isUnchanged] will be true).
 */
public val Project.kotlinFutureVersion: KotlinFutureTestingVersion
    get() = (extensions.extraProperties.properties[kotlinFutureVersionProp] as Lazy<*>?)?.value as KotlinFutureTestingVersion?
        ?: error("No Kotlin future version extension found")

/**
 * The current Kotlin version and its kind, with helper methods for selecting library versions.
 */
public data class KotlinFutureTestingVersion(
    public val versionKind: KotlinVersionKind,
    public val version: String,
    public val originalVersion: String
) {

    /**
     * Get [future] if using a future version, else the original Kotlin version.
     */
    public fun ifFuture(future: String): String = select(version, future)

    /**
     * Get [future] if using a future version, else the original Kotlin version.
     *
     * [future] is called with [version].
     */
    public fun ifFuture(future: (version: String) -> String): String = select(version, future)

    /**
     * Get [future] if using a future version, else [normal].
     */
    public fun <T> select(normal: T, future: T): T = if (isFuture) future else normal

    /**
     * Get [future] if using a future version, else [normal].
     */
    public operator fun <T> invoke(normal: T, future: T): T = select(normal, future)

    /**
     * Get [future] if using a future version, else [normal].
     *
     * [future] is called with [version].
     */
    public inline fun <T> select(normal: T, future: (version: String) -> T): T =
        if (isFuture) future(version) else normal

    /**
     * Get [future] if using a future version, else [normal].
     *
     * [future] is called with [version].
     */
    public inline operator fun <T> invoke(normal: T, future: (version: String) -> T): T = select(normal, future)

    /**
     * Get [Pair.second] if using a future version, else [Pair.first].
     */
    public infix fun <T> select(pair: Pair<T, T>): T = select(pair.first, pair.second)

    /**
     * Get [Pair.second] if using a future version, else [Pair.first].
     */
    public operator fun <T> invoke(pair: Pair<T, T>): T = select(pair)

    /**
     * Whether we are using a future EAP version.
     */
    public inline val isEap: Boolean get() = versionKind == KotlinVersionKind.Eap

    /**
     * Whether we are using a future bootstrap version.
     */
    public inline val isBootstrap: Boolean get() = versionKind == KotlinVersionKind.Bootstrap

    /**
     * Whether we are using a future version.
     */
    public inline val isFuture: Boolean get() = versionKind != KotlinVersionKind.Unchanged

    /**
     * Whether we are using the configured version (i.e. not a future version).
     */
    public inline val isUnchanged: Boolean get() = versionKind == KotlinVersionKind.Unchanged
}