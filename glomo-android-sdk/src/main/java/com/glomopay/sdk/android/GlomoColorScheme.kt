package com.glomopay.sdk.android

import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate

/**
 * The colour scheme the host app asks the checkout page to present.
 *
 * A request, not a switch: the page decides, against the merchant's own dashboard settings, so
 * [DARK] does not guarantee a dark checkout.
 *
 * Named with the `Glomo` prefix because `ColorScheme` is Compose Material's and already means a
 * palette in most host apps.
 */
public enum class GlomoColorScheme {
    /**
     * Follow the host app's night mode. The default.
     *
     * Read from `AppCompatDelegate.getDefaultNightMode()` first, falling back to the checkout
     * activity's [Configuration]. The delegate is what an app sets when it has its own light/dark
     * toggle, and it is process-wide, so a light-only app on a dark device gets a light checkout.
     *
     * What `AUTO` derives from is a per-platform decision and differs across the Glomo SDKs on
     * purpose; only [LIGHT] and [DARK] are contractual.
     */
    AUTO,

    /** Ask for light, whatever the host app is doing. */
    LIGHT,

    /** Ask for dark, whatever the host app is doing. */
    DARK,
}

/**
 * The two values the checkout page ever sees.
 *
 * A separate type from [GlomoColorScheme] so "never send `auto` on the wire" is a compile-time
 * guarantee rather than a comment: this one cannot hold a third value.
 */
internal enum class ResolvedColorScheme(val wireName: String) {
    LIGHT("light"),
    DARK("dark"),
}

/**
 * Reads the night bit out of a [Configuration.uiMode].
 *
 * Takes the raw int rather than a [Configuration] so it is reachable from a JVM unit test - the
 * mask constants are compile-time and inline, the class is not.
 */
internal fun nightModeOf(uiMode: Int): ResolvedColorScheme =
    if (uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) {
        ResolvedColorScheme.DARK
    } else {
        ResolvedColorScheme.LIGHT
    }

/**
 * The host app's night mode, from its own setting where it has one.
 *
 * [defaultNightMode] is `AppCompatDelegate.getDefaultNightMode()`: process-wide, so it reports the
 * host's choice even though the checkout runs in this SDK's own plain Activity. From API 31 that
 * call also applies a per-app `UiModeManager` override which does reach [uiMode], but below it the
 * SDK's Activity sees only the system value - which is the case this exists for.
 *
 * `MODE_NIGHT_AUTO_BATTERY` and `MODE_NIGHT_FOLLOW_SYSTEM` fall through to [uiMode]: the host has
 * not picked a side, so the configuration is the best answer available.
 *
 * Two ints rather than a Context so it is reachable from a JVM unit test.
 */
internal fun hostNightMode(defaultNightMode: Int, uiMode: Int): ResolvedColorScheme =
    when (defaultNightMode) {
        AppCompatDelegate.MODE_NIGHT_NO -> ResolvedColorScheme.LIGHT
        AppCompatDelegate.MODE_NIGHT_YES -> ResolvedColorScheme.DARK
        else -> nightModeOf(uiMode)
    }

/** Resolves the merchant's setting against the host app's current night mode. */
internal fun GlomoColorScheme.resolve(defaultNightMode: Int, uiMode: Int): ResolvedColorScheme =
    when (this) {
        GlomoColorScheme.LIGHT -> ResolvedColorScheme.LIGHT
        GlomoColorScheme.DARK -> ResolvedColorScheme.DARK
        GlomoColorScheme.AUTO -> hostNightMode(defaultNightMode, uiMode)
    }
