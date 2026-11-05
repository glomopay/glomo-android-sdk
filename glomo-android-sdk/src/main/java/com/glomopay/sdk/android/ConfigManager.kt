package com.glomopay.sdk.android

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** URL and environment behavior mirrored from the Flutter ConfigManager. */
public object ConfigManager {
    private const val DEFAULT_LRS_BASE_URL = "https://lrs-checkout.glomopay.com/"
    private const val DEFAULT_STANDARD_BASE_URL = "https://checkout.glomopay.com/"
    private const val CAROUSEL_BASE_URL = "https://glomopay-utilities.web.app/lrs-education-carousel/"

    public fun getBaseUrl(config: GlomoPayConfig, orderType: String = "standard"): String {
        val customServer = config.server
        if (!customServer.isNullOrEmpty()) {
            return if (customServer.endsWith('/')) customServer else "$customServer/"
        }
        return if (orderType.lowercase() == "lrs") DEFAULT_LRS_BASE_URL else DEFAULT_STANDARD_BASE_URL
    }

    public fun getCheckoutUrl(config: GlomoPayConfig, orderType: String = "standard"): String =
        getCheckoutUrl(config, orderType, colorScheme = null, journeyId = null)

    /**
     * [colorScheme] is the already-resolved scheme; `AUTO` never reaches here. Omitted when null,
     * which leaves the page on its own default.
     *
     * Internal because the resolved type is: resolving needs the activity's configuration, so no
     * caller outside this SDK is in a position to supply one. A scheme change mid-checkout must
     * NOT rebuild this URL - a reload destroys the payment. `GlomoPayInjectionScripts.colorScheme`
     * is the live path.
     */
    internal fun getCheckoutUrl(
        config: GlomoPayConfig,
        orderType: String,
        colorScheme: ResolvedColorScheme?,
        journeyId: String? = null,
    ): String {
        val checkoutId = config.checkoutId ?: ""
        return withQuery(
            getBaseUrl(config, orderType),
            buildList {
                add("orderId" to checkoutId)
                add("publicKey" to config.publicKey)
                add("mode" to getMode(config.publicKey))
                // Three names for one concept, by design: the property is `appColorScheme`, the
                // wire is `colorScheme`, the page hook is `glomoSetColorScheme`. The README says
                // so, so nobody assumes the wire was renamed with the property.
                if (colorScheme != null) add("colorScheme" to colorScheme.wireName)
                journeyId?.takeIf(String::isNotBlank)?.let { add("journeyId" to it) }
            },
        )
    }

    internal fun getCheckoutDocumentStartOriginRules(
        config: GlomoPayConfig,
        orderType: String = "standard",
    ): Set<String> = checkoutDocumentStartOriginRules(getCheckoutUrl(config, orderType))

    internal fun checkoutDocumentStartOriginRules(url: String): Set<String> =
        originOf(url)?.let { setOf(it) } ?: emptySet()

    /**
     * `scheme://host[:port]`, or null for anything that is not an http(s) URL.
     *
     * The scheme's default port is dropped, so the two spellings of one origin compare equal.
     * This is compared against [android.webkit.WebView.url], which reports whatever the page
     * actually navigated to - an explicit `:443` from a redirect must still match a base URL
     * written without one.
     */
    internal fun originOf(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase() ?: return null
        if (scheme != "https" && scheme != "http") return null
        val defaultPort = if (scheme == "https") 443 else 80
        val port = uri.port.takeIf { it >= 0 && it != defaultPort }?.let { ":$it" } ?: ""
        return "$scheme://$host$port"
    }

    /**
     * Every origin this SDK serves a page from, for the config in hand.
     *
     * Derived from the same base URLs the SDK navigates to rather than written out, so a merchant
     * on a custom [GlomoPayConfig.server] is covered without a second list that can drift from the
     * first. All three are trusted for every WebView rather than one per journey: the set still
     * excludes every bank, which is the boundary that matters, and a narrower list would start
     * dropping our own pages the first time a journey moves to a sibling host.
     */
    internal fun trustedPageOrigins(config: GlomoPayConfig): Set<String> =
        setOfNotNull(
            originOf(getBaseUrl(config, "standard")),
            originOf(getBaseUrl(config, "lrs")),
            originOf(CAROUSEL_BASE_URL),
        )

    /**
     * Domains GlomoPay serves checkout from.
     *
     * A suffix rule rather than a fixed host list, because a shipped SDK cannot be updated when a
     * checkout host moves or redirects between these two - an exact list would strand every
     * installed version on the old host the day the move happens. The Flutter SDK's
     * `CheckoutOriginPolicy` has worked this way since KAN-8742; this is the same rule.
     */
    internal val GLOMO_DOMAINS: List<String> = listOf("glomopay.com", "glomo.one")

    /** Exact host, or a subdomain of one. Never a bare suffix - `attackerglomopay.com` is not ours. */
    internal fun isGlomoHost(host: String): Boolean {
        val lower = host.lowercase()
        return GLOMO_DOMAINS.any { lower == it || lower.endsWith(".$it") }
    }

    /**
     * Whether [url] is one of ours.
     *
     * Two sources of trust, and the second is what keeps the first from having to be exhaustive:
     * - https on a GlomoPay domain, default port only. Survives a domain move.
     * - an exact origin the SDK itself navigated to, from [trustedPageOrigins]. That is what
     *   covers the education carousel on Firebase Hosting and a merchant's own
     *   [GlomoPayConfig.server], neither of which is on a GlomoPay domain.
     */
    internal fun isTrustedPageOrigin(url: String?, trusted: Set<String>): Boolean {
        val origin = url?.let(::originOf) ?: return false
        if (origin in trusted) return true
        // Default port only: `originOf` keeps an explicit non-default port, so a port here means
        // the origin is not plain https on the domain and the suffix rule must not cover it.
        val host = origin.removePrefix("https://")
        if (host == origin || ':' in host) return false
        return isGlomoHost(host)
    }

    /** [colorScheme] carries the same resolved value on the same param name - the carousel is on
     * the same contract as the checkout pages. */
    internal fun getCarouselUrl(
        config: GlomoPayConfig,
        colorScheme: ResolvedColorScheme? = null,
    ): String =
        withQuery(
            CAROUSEL_BASE_URL,
            buildList {
                add("orderId" to (config.orderId ?: ""))
                add("publicKey" to config.publicKey)
                if (colorScheme != null) add("colorScheme" to colorScheme.wireName)
            },
        )

    public fun getMode(publicKey: String): String = when {
        publicKey.startsWith("test_") || publicKey.startsWith("mock_") -> "mock"
        else -> "live"
    }

    public fun isTestOrMock(publicKey: String): Boolean =
        publicKey.startsWith("test_") || publicKey.startsWith("mock_")

    /** Detects checkout type using the same order response heuristic as Flutter. */
    public fun detectOrderType(orderData: Map<String, Any?>): String {
        val explicit = orderData["orderType"]?.toString()?.takeIf { it.isNotBlank() }
        if (explicit != null) return explicit
        return if (orderData.containsKey("lrs") && orderData["lrs"] != null) "lrs" else "standard"
    }

    /**
     * Whether the merchant has dark mode switched on, from the order response.
     *
     * The key is snake_case and has no camelCase alias, unlike `order_type`/`orderType` on the
     * same payload - a reader guessing one finds nothing and silently stays light.
     *
     * An identity check against `true` rather than a truthiness one: the map value arrives as
     * `Any?`, so a missing key, a null, the string "true" and a number are all possible and all
     * mean light. Light is the safe answer, because the page renders light whenever the flag is
     * off - dark is the only value that can be visibly wrong.
     *
     * `as? Boolean` is a real identity check on this input, which is not free everywhere: the
     * map comes from `org.json.JSONObject`, whose `get` returns `java.lang.Boolean` for a JSON
     * boolean and `java.lang.Integer` for `1`, so a number casts to null rather than converting.
     * A decoder that boxes booleans and numbers into one type would need an explicit check.
     */
    internal fun darkModeEnabled(orderData: Map<String, Any?>): Boolean =
        orderData["dark_mode_enabled"] as? Boolean == true

    private fun withQuery(baseUrl: String, parameters: List<Pair<String, String>>): String {
        val query = parameters.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        val fragmentStart = baseUrl.indexOf('#')
        val fragment = if (fragmentStart >= 0) baseUrl.substring(fragmentStart) else ""
        val withoutFragment = if (fragmentStart >= 0) baseUrl.substring(0, fragmentStart) else baseUrl
        val withoutQuery = withoutFragment.substringBefore('?')
        return "$withoutQuery?$query$fragment"
    }

    internal fun withJourneyId(url: String, journeyId: String?): String {
        if (journeyId.isNullOrBlank()) return url
        val fragmentStart = url.indexOf('#')
        val fragment = if (fragmentStart >= 0) url.substring(fragmentStart) else ""
        val withoutFragment = if (fragmentStart >= 0) url.substring(0, fragmentStart) else url
        val parameters = withoutFragment.substringAfter('?', "")
            .split('&')
            .filter { it.isNotEmpty() && it.substringBefore('=') != "journeyId" }
        val query = (parameters + "journeyId=${encode(journeyId)}").joinToString("&")
        return "${withoutFragment.substringBefore('?')}?$query$fragment"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
}
