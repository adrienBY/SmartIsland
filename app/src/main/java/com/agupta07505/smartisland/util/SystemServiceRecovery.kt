/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.agupta07505.smartisland.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import com.agupta07505.smartisland.service.SmartIslandNotificationListenerService
import com.agupta07505.smartisland.service.SmartIslandOverlayService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Reconnects system-managed services after the package leaves Android's force-stopped state, or after an OEM battery
 * manager killed or froze the process.
 *
 * Notification listener: `NotificationListenerService.requestRebind()` only works after the listener itself called
 * `requestUnbind()` (the system otherwise sees "already enabled, nothing to do"), so after a process kill or a stale
 * binding it never brings the listener back. Toggling the listener component's enabled state is what really makes
 * the system unbind and bind it again, the same thing switching notification access off and on does by hand.
 *
 * AccessibilityService has no rebind API at all, so for an already-authorized service we issue a harmless
 * component-state change between DEFAULT and ENABLED. Both states leave the manifest-enabled component available,
 * while the package-change signal gives AccessibilityManager/OEM managers a chance to bind it again.
 */
object SystemServiceRecovery {
    private val accessibilityRefreshAttempted = AtomicBoolean(false)
    private val lastListenerRefreshAt = AtomicLong(Long.MIN_VALUE / 2)
    private val pending = AtomicBoolean(false)

    /** Binder calls (the probe, component toggles) and the grace wait run here, never on the main thread. */
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "SystemServiceRecovery").apply { isDaemon = true } }

    fun resetAccessibilityRefreshAttempt() {
        accessibilityRefreshAttempted.set(false)
    }

    /** Cheap to call often (screen on, unlock, app opened): checks run at most one at a time, off the main thread. */
    fun requestRecovery(context: Context) {
        val appContext = context.applicationContext
        if (!pending.compareAndSet(false, true)) return
        runCatchingLogged(TAG, "System-service recovery could not be scheduled") {
            worker.execute {
                try {
                    runCatchingLogged(TAG, "System-service recovery failed") {
                        recoverNotificationListener(appContext)

                        if (!SmartIslandOverlayService.isSystemConnected &&
                            isAccessibilityPermissionGranted(appContext) &&
                            accessibilityRefreshAttempted.compareAndSet(false, true)
                        ) {
                            refreshAccessibilityComponent(appContext)
                        }
                    }
                } finally {
                    pending.set(false)
                }
            }
        } ?: pending.set(false)
    }

    private fun listenerHealthy(): Boolean =
        SmartIslandNotificationListenerService.isSystemConnected &&
            SmartIslandNotificationListenerService.probeBinding()

    private fun recoverNotificationListener(context: Context) {
        if (!isNotificationListenerPermissionGranted(context) || listenerHealthy()) return

        // A process the system just started binds its listener within a second or two by itself, and a disconnect
        // may be the user switching access off: wait, then look again before forcing anything.
        Thread.sleep(LISTENER_GRACE_MS)
        if (!isNotificationListenerPermissionGranted(context) || listenerHealthy()) return

        val now = SystemClock.elapsedRealtime()
        val last = lastListenerRefreshAt.get()
        if (now - last < LISTENER_REFRESH_GAP_MS || !lastListenerRefreshAt.compareAndSet(last, now)) return

        val component = ComponentName(context, SmartIslandNotificationListenerService::class.java)
        AppLogRecorder.w(
            "ListenerLifecycle",
            "listener has access but is not bound (connected=${SmartIslandNotificationListenerService.isSystemConnected}): " +
                "toggling its component to force the system to bind it again"
        )
        // Harmless when it does nothing; works on its own when the listener had unbound itself.
        runCatchingLogged(TAG, "Notification-listener rebind request failed") {
            NotificationListenerService.requestRebind(component)
        }
        runCatchingLogged(TAG, "Notification-listener component toggle failed") {
            val pm = context.packageManager
            val restoreTo = when (pm.getComponentEnabledSetting(component)) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                // Disabled on purpose by someone else: leave it alone.
                else -> return@runCatchingLogged
            }
            pm.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            pm.setComponentEnabledSetting(component, restoreTo, PackageManager.DONT_KILL_APP)
        }
    }

    private fun refreshAccessibilityComponent(context: Context) {
        runCatchingLogged(TAG, "Accessibility component refresh failed") {
            val component = ComponentName(context, SmartIslandOverlayService::class.java)
            val packageManager = context.packageManager
            val currentState = packageManager.getComponentEnabledSetting(component)
            val refreshedState = when (currentState) {
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT ->
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED ->
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                else -> return@runCatchingLogged
            }
            packageManager.setComponentEnabledSetting(
                component,
                refreshedState,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    private fun isAccessibilityPermissionGranted(context: Context): Boolean {
        val expected = ComponentName(context, SmartIslandOverlayService::class.java)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(':').any {
            ComponentName.unflattenFromString(it) == expected
        }
    }

    private fun isNotificationListenerPermissionGranted(context: Context): Boolean {
        val expected = ComponentName(context, SmartIslandNotificationListenerService::class.java)
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return enabledListeners.split(':').any {
            ComponentName.unflattenFromString(it) == expected
        }
    }

    private const val TAG = "SystemServiceRecovery"
    /** How long a missing listener may take to come back by itself before it is forced. */
    private const val LISTENER_GRACE_MS = 3_000L
    /** At most one forced rebind per minute, so a listener that keeps failing is not toggled in a loop. */
    private const val LISTENER_REFRESH_GAP_MS = 60_000L
}
