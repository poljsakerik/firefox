/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.mirlumi.allowlist

import android.content.Context
import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import app.mirlumi.settingslock.SettingsLock
import java.lang.ref.WeakReference
import mozilla.components.concept.engine.request.RequestInterceptor
import mozilla.components.concept.engine.webextension.MessageHandler
import mozilla.components.concept.engine.webextension.Port
import mozilla.components.concept.engine.webextension.WebExtensionRuntime
import mozilla.components.support.webextensions.BuiltInWebExtensionController
import org.json.JSONArray
import org.json.JSONObject

/**
 * Allowlist-only browsing. A built-in extension sends every top-level page that isn't on the bundled list, or on the
 * list a parent added to, to a blocked page; the matcher and the page come from packages/allowlist. From the blocked
 * page a parent can allow the site with the Settings PIN. In [OpenMode], which a parent can turn on, other pages open
 * and the extension blocks those the page classifier from packages/page-classifier flags.
 */
object Allowlist {
    private const val EXTENSION_ID = "allowlist@mirlumi.app"
    private const val EXTENSION_URL = "resource://android/assets/extensions/mirlumi-allowlist/"
    private const val PORT_NAME = "mirlumiAllowlist"

    private val controller = BuiltInWebExtensionController(EXTENSION_ID, EXTENSION_URL, PORT_NAME)
    private var sites: ParentSites? = null
    private var openMode: OpenMode? = null
    private var port: Port? = null
    private var activity: WeakReference<FragmentActivity>? = null
    private val sendSites: (List<String>) -> Unit = { sites -> port?.postMessage(AllowlistMessages.parentSites(sites)) }
    private val sendMode: (Boolean) -> Unit = { open -> port?.postMessage(AllowlistMessages.mode(open)) }

    /** Whether allowlist-only browsing is on, as passed to [install]. */
    var isEnabled = false
        @VisibleForTesting internal set

    /** Whether a parent can turn on open mode, as passed to [install]. */
    var isOpenModeAvailable = false
        @VisibleForTesting internal set

    // Set while the extension's background script is connected, which it does only after its request listener is in
    // place. Until then, and if installing it fails, nothing could block a page, so top-level loads are refused.
    @Volatile @VisibleForTesting internal var extensionReady = false

    /**
     * Installs the extension in [runtime], or removes it when [enabled] is false. [openModeAvailable] lets a parent
     * turn on open mode. Call once the engine exists.
     */
    fun install(context: Context, runtime: WebExtensionRuntime, enabled: Boolean, openModeAvailable: Boolean = false) {
        isEnabled = enabled
        isOpenModeAvailable = openModeAvailable
        if (!enabled) {
            runtime.listInstalledWebExtensions(
                onSuccess = { installed ->
                    installed.firstOrNull { it.id == EXTENSION_ID }?.let { runtime.uninstallWebExtension(it) }
                }
            )
            return
        }
        parentSites(context).addListener(sendSites)
        openMode(context).addListener(sendMode)
        controller.registerBackgroundMessageHandler(Messages(context.applicationContext), PORT_NAME)
        controller.install(runtime)
    }

    /**
     * Refuses a top-level load the extension can't judge: any web page before the extension is ready, and the hosts
     * Gecko hides from extensions. Call first from the engine's request interceptor; null lets the load go on.
     */
    fun interceptLoad(uri: String, isSubframeRequest: Boolean): RequestInterceptor.InterceptionResponse? {
        if (!isEnabled || isSubframeRequest) return null
        val parsed = Uri.parse(uri)
        val scheme = parsed.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        val host = parsed.host?.lowercase()?.removeSuffix(".")
        return if (!extensionReady || GeckoRestrictedHosts.contains(host)) {
            RequestInterceptor.InterceptionResponse.Deny
        } else {
            null
        }
    }

    /**
     * Checks the address an interceptor's [response] replaces the load with. A replacement loads past the request
     * interceptor, so one [interceptLoad] would refuse is refused here, and an app link loses a fallback address it
     * would refuse. Call on every response the engine's request interceptor returns.
     */
    fun checkReplacement(response: RequestInterceptor.InterceptionResponse?): RequestInterceptor.InterceptionResponse? =
        when (response) {
            is RequestInterceptor.InterceptionResponse.Url ->
                interceptLoad(response.url, isSubframeRequest = false) ?: response
            is RequestInterceptor.InterceptionResponse.AppIntent -> {
                val fallback = response.fallbackUrl
                val refused = fallback != null && interceptLoad(fallback, isSubframeRequest = false) != null
                if (refused) response.copy(fallbackUrl = null) else response
            }
            else -> response
        }

    /** Lets the PIN screen open over [activity] while it's started. Call from the activity's `onCreate`. */
    fun attach(activity: FragmentActivity) {
        activity.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    this@Allowlist.activity = WeakReference(activity)
                }

                override fun onDestroy(owner: LifecycleOwner) {
                    if (this@Allowlist.activity?.get() === activity) this@Allowlist.activity = null
                }
            }
        )
    }

    @Synchronized
    internal fun openMode(context: Context): OpenMode =
        openMode ?: OpenMode(PreferencesOpenModeStore(context), isOpenModeAvailable).also { openMode = it }

    @Synchronized
    internal fun parentSites(context: Context): ParentSites =
        sites ?: ParentSites(PreferencesSiteStore(context)).also { sites = it }

    private fun requestAllow(context: Context, entry: String, done: (AllowlistMessages.Outcome) -> Unit) {
        val activity =
            activity?.get()?.takeIf { it.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
                ?: return done(AllowlistMessages.Outcome.FAILED)
        SettingsLock.authorize(activity.supportFragmentManager, onDenied = { done(AllowlistMessages.Outcome.DENIED) }) {
            parentSites(context).add(entry)
            done(AllowlistMessages.Outcome.GRANTED)
        }
    }

    private class Messages(private val context: Context) : MessageHandler {
        override fun onPortConnected(port: Port) {
            Allowlist.port = port
            extensionReady = true
            // The mode first: the extension treats pages as allowlist-only until the parent's sites arrive.
            port.postMessage(AllowlistMessages.mode(openMode(context).isOpen))
            port.postMessage(AllowlistMessages.parentSites(parentSites(context).sites))
        }

        override fun onPortDisconnected(port: Port) {
            Allowlist.port = null
            extensionReady = false
        }

        override fun onPortMessage(message: Any, port: Port) {
            val request = AllowlistMessages.allowRequest(message) ?: return
            requestAllow(context, request.entry) { outcome ->
                port.postMessage(AllowlistMessages.allowResult(request.id, outcome))
            }
        }
    }
}

/**
 * The hosts Gecko never shows extensions, from the `extensions.webextensions.restrictedDomains` pref in
 * `modules/libpref/init/all.js`. They're Mozilla's account, add-on and support sites, so they're refused outright
 * rather than shown the blocked page, where a parent could allow them in vain.
 */
internal object GeckoRestrictedHosts {
    val hosts =
        setOf(
            "accounts-static.cdn.mozilla.net",
            "accounts.firefox.com",
            "addons.cdn.mozilla.net",
            "addons.mozilla.org",
            "api.accounts.firefox.com",
            "content.cdn.mozilla.net",
            "discovery.addons.mozilla.org",
            "oauth.accounts.firefox.com",
            "profile.accounts.firefox.com",
            "support.mozilla.org",
            "sync.services.mozilla.com",
        )

    fun contains(host: String?): Boolean = host in hosts
}

/** The messages between the app and the extension's background script. */
internal object AllowlistMessages {
    enum class Outcome {
        GRANTED,
        DENIED,
        FAILED,
    }

    data class AllowRequest(val id: Int, val entry: String)

    fun parentSites(sites: List<String>): JSONObject =
        JSONObject().put("type", "parentSites").put("sites", JSONArray(sites))

    fun mode(open: Boolean): JSONObject = JSONObject().put("type", "mode").put("open", open)

    fun allowRequest(message: Any): AllowRequest? {
        val json = message as? JSONObject ?: return null
        if (json.optString("type") != "allow") return null
        val id = json.optInt("id", -1).takeIf { it >= 0 } ?: return null
        val entry = json.optString("entry").takeIf(ParentSites::isPlausibleEntry) ?: return null
        return AllowRequest(id, entry)
    }

    fun allowResult(id: Int, outcome: Outcome): JSONObject =
        JSONObject()
            .put("type", "allowResult")
            .put("id", id)
            .put("granted", outcome == Outcome.GRANTED)
            .put("failed", outcome == Outcome.FAILED)
}
