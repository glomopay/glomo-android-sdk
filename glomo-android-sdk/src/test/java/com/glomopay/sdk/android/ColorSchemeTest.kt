package com.glomopay.sdk.android

import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import com.glomopay.sdk.android.bridge.GlomoPayInjectionScripts
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The night bit, as the framework packs it into `Configuration.uiMode`. */
private const val NIGHT_YES = Configuration.UI_MODE_NIGHT_YES
private const val NIGHT_NO = Configuration.UI_MODE_NIGHT_NO

/** The host has expressed no preference of its own, so the configuration decides. */
private const val HOST_UNSET = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM

private fun queryParameters(url: String): Map<String, String> =
    URI(url).query.orEmpty().split("&").filter { it.isNotEmpty() }.associate {
        val (key, value) = it.split("=", limit = 2)
        key to value
    }

class ColorSchemeResolutionTest {
    @Test
    fun explicit_schemes_ignore_the_host() {
        assertEquals(ResolvedColorScheme.LIGHT, GlomoColorScheme.LIGHT.resolve(HOST_UNSET, NIGHT_YES))
        assertEquals(ResolvedColorScheme.DARK, GlomoColorScheme.DARK.resolve(HOST_UNSET, NIGHT_NO))
    }

    @Test
    fun auto_follows_the_host_night_mode() {
        assertEquals(ResolvedColorScheme.DARK, GlomoColorScheme.AUTO.resolve(HOST_UNSET, NIGHT_YES))
        assertEquals(ResolvedColorScheme.LIGHT, GlomoColorScheme.AUTO.resolve(HOST_UNSET, NIGHT_NO))
    }

    @Test
    fun an_undefined_night_mode_is_light_not_an_error() {
        // UI_MODE_NIGHT_UNDEFINED, and anything else in the other uiMode bits, must not read as
        // dark - the page would go dark on a device that never asked.
        assertEquals(ResolvedColorScheme.LIGHT, GlomoColorScheme.AUTO.resolve(HOST_UNSET, 0))
        assertEquals(
            ResolvedColorScheme.LIGHT,
            GlomoColorScheme.AUTO.resolve(HOST_UNSET, Configuration.UI_MODE_TYPE_CAR),
        )
    }

    @Test
    fun the_other_ui_mode_bits_do_not_disturb_the_night_bit() {
        // A car or desk dock sets UI_MODE_TYPE bits alongside the night bit; only the night mask
        // is ours to read.
        assertEquals(
            ResolvedColorScheme.DARK,
            GlomoColorScheme.AUTO.resolve(HOST_UNSET, Configuration.UI_MODE_TYPE_DESK or NIGHT_YES),
        )
    }

    @Test
    fun a_light_only_app_on_a_dark_device_gets_a_light_checkout() {
        // The case the activity's own uiMode gets wrong below API 31: the host has called
        // AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_NO), the system is dark, and the SDK
        // runs in a plain Activity that only sees the system value.
        assertEquals(
            ResolvedColorScheme.LIGHT,
            GlomoColorScheme.AUTO.resolve(AppCompatDelegate.MODE_NIGHT_NO, NIGHT_YES),
        )
    }

    @Test
    fun a_dark_only_app_on_a_light_device_gets_a_dark_checkout() {
        assertEquals(
            ResolvedColorScheme.DARK,
            GlomoColorScheme.AUTO.resolve(AppCompatDelegate.MODE_NIGHT_YES, NIGHT_NO),
        )
    }

    @Test
    fun a_host_with_no_preference_falls_back_to_the_configuration() {
        // FOLLOW_SYSTEM and AUTO_BATTERY both mean the host has not picked a side. On API 31+ the
        // per-app override makes uiMode authoritative anyway; below it this is the best available.
        for (mode in listOf(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY,
            AppCompatDelegate.MODE_NIGHT_UNSPECIFIED,
        )) {
            assertEquals(ResolvedColorScheme.DARK, GlomoColorScheme.AUTO.resolve(mode, NIGHT_YES))
            assertEquals(ResolvedColorScheme.LIGHT, GlomoColorScheme.AUTO.resolve(mode, NIGHT_NO))
        }
    }

    @Test
    fun an_explicit_scheme_ignores_the_host_night_mode_too() {
        assertEquals(
            ResolvedColorScheme.LIGHT,
            GlomoColorScheme.LIGHT.resolve(AppCompatDelegate.MODE_NIGHT_YES, NIGHT_YES),
        )
        assertEquals(
            ResolvedColorScheme.DARK,
            GlomoColorScheme.DARK.resolve(AppCompatDelegate.MODE_NIGHT_NO, NIGHT_NO),
        )
    }

    @Test
    fun the_wire_vocabulary_is_exactly_light_and_dark() {
        assertEquals("light", ResolvedColorScheme.LIGHT.wireName)
        assertEquals("dark", ResolvedColorScheme.DARK.wireName)
    }

    @Test
    fun the_default_is_auto() {
        assertEquals(GlomoColorScheme.AUTO, GlomoPayConfig("live_key", orderId = "order_1").appColorScheme)
    }
}

class ColorSchemeUrlTest {
    private val config = GlomoPayConfig("live_key", orderId = "order_1")

    @Test
    fun the_resolved_scheme_reaches_the_checkout_url() {
        assertEquals(
            "dark",
            queryParameters(
                ConfigManager.getCheckoutUrl(config, "standard", ResolvedColorScheme.DARK),
            )["colorScheme"],
        )
        assertEquals(
            "light",
            queryParameters(
                ConfigManager.getCheckoutUrl(config, "lrs", ResolvedColorScheme.LIGHT),
            )["colorScheme"],
        )
    }

    @Test
    fun the_carousel_takes_it_on_the_same_param() {
        assertEquals(
            "dark",
            queryParameters(ConfigManager.getCarouselUrl(config, ResolvedColorScheme.DARK))["colorScheme"],
        )
    }

    @Test
    fun it_is_omitted_when_unresolved_rather_than_sent_empty() {
        assertFalse("colorScheme" in queryParameters(ConfigManager.getCheckoutUrl(config)))
        assertFalse("colorScheme" in queryParameters(ConfigManager.getCarouselUrl(config)))
    }

    @Test
    fun it_does_not_disturb_the_params_the_page_already_reads() {
        val parameters = queryParameters(
            ConfigManager.getCheckoutUrl(config, "standard", ResolvedColorScheme.DARK),
        )
        assertEquals("order_1", parameters["orderId"])
        assertEquals("live_key", parameters["publicKey"])
        assertEquals("live", parameters["mode"])
        assertEquals("dark", parameters["colorScheme"])
    }
}

class ColorSchemeInjectionTest {
    @Test
    fun it_guards_the_setter_not_window() {
        // `window` always exists in a WebView; the setter is what is absent during load or on an
        // older merchant deploy, so `window?.` would throw where this skips.
        val script = GlomoPayInjectionScripts.colorScheme(ResolvedColorScheme.DARK)
        assertTrue("typeof window.glomoSetColorScheme === 'function'" in script)
        assertFalse("window?." in script)
    }

    @Test
    fun it_sends_nothing_but_the_setter_call() {
        // No parked global: no page reads one, so it would be a contract with no consumer. No
        // read-back either - a flag that is true whenever the setter returned says nothing about
        // whether the page changed, because the page weighs the request against the merchant's
        // own dashboard settings.
        val script = GlomoPayInjectionScripts.colorScheme(ResolvedColorScheme.DARK)
        assertFalse("__glomoColorScheme" in script)
        assertFalse("return" in script)
    }

    @Test
    fun it_emits_the_wire_vocabulary() {
        assertTrue("'light'" in GlomoPayInjectionScripts.colorScheme(ResolvedColorScheme.LIGHT))
        assertTrue("'dark'" in GlomoPayInjectionScripts.colorScheme(ResolvedColorScheme.DARK))
    }

    @Test
    fun it_has_no_run_once_guard_unlike_the_injections_around_it() {
        // Re-run on every page load and every change; a guard would pin the page to whatever it
        // saw first.
        assertFalse("Injected__" in GlomoPayInjectionScripts.colorScheme(ResolvedColorScheme.DARK))
    }

    @Test
    fun it_cannot_throw_into_the_page() {
        assertTrue("catch (e) {}" in GlomoPayInjectionScripts.colorScheme(ResolvedColorScheme.DARK))
    }
}

class ColorSchemeOriginTest {
    @Test
    fun an_origin_is_scheme_host_and_port() {
        assertEquals("https://checkout.glomopay.com", ConfigManager.originOf("https://checkout.glomopay.com/?a=1"))
        assertEquals("http://localhost:4444", ConfigManager.originOf("http://localhost:4444/x"))
    }

    @Test
    fun a_different_host_is_a_different_origin() {
        // The gate on re-applying a scheme after a page load: a bank page reached by redirect is
        // not ours to theme.
        assertFalse(
            ConfigManager.originOf("https://checkout.glomopay.com/") ==
                ConfigManager.originOf("https://acs.examplebank.com/3ds"),
        )
    }

    @Test
    fun a_non_http_url_has_no_origin_rather_than_a_fabricated_one() {
        assertNull(ConfigManager.originOf("about:blank"))
        assertNull(ConfigManager.originOf("not a url"))
    }
}
