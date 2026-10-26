package com.glomopay.sdk.android.ui

import androidx.annotation.ColorInt
import com.glomopay.sdk.android.GlomoColorScheme

/**
 * The colours the SDK's own chrome paints with, in both schemes.
 *
 * `internal`, and it stays that way. The checkout's chrome is deliberately not host-configurable:
 * this Activity runs inside a merchant app, and the one control a user needs when a payment goes
 * wrong must not be restyleable into invisibility. Centralising the values makes them easy for
 * *us* to change; it is not an invitation to expose them. Keeping it internal also keeps it out of
 * `api/glomo-android-sdk.api`, so none of this is a published signature.
 *
 * Every value is a checkout theme token read from `packages/theme` in the web monorepo, so the
 * frame the SDK draws and the page inside it come from one source. The token each entry is taken
 * from is named on it, and `SdkColorsTest` asserts the hexes, so a typo fails the build rather
 * than quietly restyling a screen.
 *
 * Deliberately **not** `res/values/colors.xml` with a `-night` qualifier. A resource qualifier
 * keys off the device configuration, and the device is the wrong source: the merchant's
 * [GlomoColorScheme] decides, because a dark frame around a checkout page that came back light is
 * a defect rather than a theme.
 */
internal class SdkColors private constructor(
    /** `--bg-default`. Every full-bleed surface the SDK paints: overlays, panels, the flow bar. */
    @ColorInt val surface: Int,
    /** `--text-secondary`. The loading labels and the connection-error title. */
    @ColorInt val textSecondary: Int,
    /** `--text-tertiary`. The connection-error message. */
    @ColorInt val textTertiary: Int,
    /** `--icon-default`. The flow overlay's back chevron, applied as a tint. */
    @ColorInt val icon: Int,
    /** `--bg-brand-solid`. Filled actions - Retry. Carries [onAction], never [surface] text. */
    @ColorInt val action: Int,
    /**
     * `--text-brand`. The same brand drawn *as* a foreground on [surface] - Cancel, the only
     * control on an unrecoverable error panel.
     *
     * Identical to [action] in light. They part in dark, where the fill is bright enough to carry
     * white text but too dark to read as text itself: `#4468DA` on `#0F172A` is 3.6:1.
     */
    @ColorInt val actionText: Int,
    /** `--text-on-brand`. Foreground on a filled [action]. */
    @ColorInt val onAction: Int,
) {
    internal companion object {
        /** Chrome for a light checkout. */
        val LIGHT: SdkColors = SdkColors(
            surface = 0xFFFFFFFF.toInt(),
            textSecondary = 0xFF475569.toInt(),
            textTertiary = 0xFF64748B.toInt(),
            icon = 0xFF334155.toInt(),
            action = 0xFF153298.toInt(),
            actionText = 0xFF153298.toInt(),
            onAction = 0xFFFFFFFF.toInt(),
        )

        /**
         * Chrome for a dark checkout.
         *
         * Slate, not black. The dark checkout page is `#0F172A`, and a black bar above it would
         * put a visible seam across the top of the screen - the opposite of what theming the
         * chrome is for.
         */
        val DARK: SdkColors = SdkColors(
            surface = 0xFF0F172A.toInt(),
            textSecondary = 0xFFCBD5E1.toInt(),
            textTertiary = 0xFF94A3B8.toInt(),
            icon = 0xFFCBD5E1.toInt(),
            action = 0xFF4468DA.toInt(),
            actionText = 0xFF7090E8.toInt(),
            onAction = 0xFFFFFFFF.toInt(),
        )

        /**
         * The palette for [scheme].
         *
         * [GlomoColorScheme.AUTO] gets [LIGHT], which is not an oversight. `auto` is resolved
         * against the host app's appearance and handed to the page as a *request* - the page may
         * still answer light, because the merchant has no dark-mode flag or the user pinned a
         * preference. Painting the frame dark off a request nothing has answered puts a dark bar
         * around a light page, which reads as a defect; a light bar around a light page merely
         * reads as plain. [GlomoColorScheme.LIGHT] and [GlomoColorScheme.DARK] are instructions
         * rather than requests, so the frame follows them.
         *
         * Keyed off the merchant's setting rather than off `ResolvedColorScheme` for the same
         * reason: the resolved value cannot tell an `AUTO` that happened to land on dark apart
         * from a merchant who asked for dark.
         */
        fun forScheme(scheme: GlomoColorScheme): SdkColors =
            if (scheme == GlomoColorScheme.DARK) DARK else LIGHT
    }
}
