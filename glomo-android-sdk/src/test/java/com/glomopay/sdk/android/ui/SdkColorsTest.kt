package com.glomopay.sdk.android.ui

import com.glomopay.sdk.android.GlomoColorScheme
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The checkout theme tokens each palette entry is taken from, as they resolve in
 * `packages/theme/semantic.css` through `packages/theme/primitives.css` in the web monorepo.
 *
 * Pinned here rather than derived: this is the only link between the SDK's palette and the page's,
 * so a web retokenisation shows up as a failure here instead of as a frame that quietly stops
 * matching the page inside it.
 */
private val WEB_TOKENS: Map<String, Pair<Int, Int>> = mapOf(
    // token to (light, dark)
    "--bg-default" to (0xFFFFFFFF.toInt() to 0xFF0F172A.toInt()),
    "--text-secondary" to (0xFF475569.toInt() to 0xFFCBD5E1.toInt()),
    "--text-tertiary" to (0xFF64748B.toInt() to 0xFF94A3B8.toInt()),
    "--icon-default" to (0xFF334155.toInt() to 0xFFCBD5E1.toInt()),
    "--bg-brand-solid" to (0xFF153298.toInt() to 0xFF4468DA.toInt()),
    "--text-brand" to (0xFF153298.toInt() to 0xFF7090E8.toInt()),
    "--text-on-brand" to (0xFFFFFFFF.toInt() to 0xFFFFFFFF.toInt()),
)

/** Which token each entry claims, and how to read it off a palette. */
private val ORIGIN: Map<String, Pair<String, (SdkColors) -> Int>> = mapOf(
    "surface" to ("--bg-default" to { c: SdkColors -> c.surface }),
    "textSecondary" to ("--text-secondary" to { c: SdkColors -> c.textSecondary }),
    "textTertiary" to ("--text-tertiary" to { c: SdkColors -> c.textTertiary }),
    "icon" to ("--icon-default" to { c: SdkColors -> c.icon }),
    "action" to ("--bg-brand-solid" to { c: SdkColors -> c.action }),
    "actionText" to ("--text-brand" to { c: SdkColors -> c.actionText }),
    "onAction" to ("--text-on-brand" to { c: SdkColors -> c.onAction }),
)

class SdkColorsTest {
    @Test
    fun `every entry is the web token it claims, in both schemes`() {
        ORIGIN.forEach { (entry, origin) ->
            val (token, read) = origin
            val (light, dark) = WEB_TOKENS.getValue(token)
            assertEquals(light, read(SdkColors.LIGHT), "$entry is not light $token")
            assertEquals(dark, read(SdkColors.DARK), "$entry is not dark $token")
        }
    }

    @Test
    fun `auto gets the light palette, dark gets dark`() {
        // `auto` is a request the page can refuse - the merchant may have no dark-mode flag - so
        // the frame stays neutral until something has answered. `dark` is an instruction, so the
        // frame follows it. Asserting the mapping rather than the colours, because this is the
        // rule the whole of the chrome work rests on.
        assertSame(SdkColors.LIGHT, SdkColors.forScheme(GlomoColorScheme.AUTO))
        assertSame(SdkColors.LIGHT, SdkColors.forScheme(GlomoColorScheme.LIGHT))
        assertSame(SdkColors.DARK, SdkColors.forScheme(GlomoColorScheme.DARK))
    }

    @Test
    fun `every foreground meets WCAG AA on the surface it is drawn on`() {
        // The connection panel's Cancel is the only control on screen when the error is not
        // recoverable, and the flow chevron is the only way off a bank page. A token that lands
        // near its own background turns either into a dead end, so assert real separation rather
        // than merely "not equal". 4.5:1 is AA for body text; everything here is 14sp or larger.
        listOf("light" to SdkColors.LIGHT, "dark" to SdkColors.DARK).forEach { (name, c) ->
            listOf(
                "textSecondary" to c.textSecondary,
                "textTertiary" to c.textTertiary,
                "icon" to c.icon,
                "actionText" to c.actionText,
            ).forEach { (entry, colour) ->
                assertTrue(
                    contrast(colour, c.surface) >= 4.5,
                    "$entry is not legible on the $name surface",
                )
            }
            assertTrue(
                contrast(c.onAction, c.action) >= 4.5,
                "onAction is not legible on a filled $name action",
            )
        }
    }

    @Test
    fun `the brand is split because the dark fill cannot carry text`() {
        // Why `actionText` exists rather than one brand entry: the dark fill is 3.6:1 on the dark
        // surface, below AA, while light has no such problem and the two values coincide there.
        assertEquals(SdkColors.LIGHT.action, SdkColors.LIGHT.actionText)
        assertTrue(contrast(SdkColors.DARK.action, SdkColors.DARK.surface) < 4.5)
        assertTrue(contrast(SdkColors.DARK.actionText, SdkColors.DARK.surface) >= 4.5)
    }
}

/** WCAG relative-luminance contrast ratio between two opaque ARGB colours. */
private fun contrast(a: Int, b: Int): Double {
    val la = luminance(a)
    val lb = luminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

private fun luminance(argb: Int): Double {
    val channels = listOf(16, 8, 0).map { shift ->
        val v = ((argb shr shift) and 0xFF) / 255.0
        if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2]
}
