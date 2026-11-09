package app.kaihi.android.core.vpn

import app.kaihi.android.BuildConfig
import app.kaihi.android.platform.mode.AppMode

/** Returns whether a custom subscription User-Agent can be sent as an HTTP header. */
fun isValidSubscriptionUserAgent(value: String): Boolean =
    value.trim().length in 1..MAX_SUBSCRIPTION_USER_AGENT_LENGTH &&
        value.all { it.code in PRINTABLE_ASCII_RANGE }

fun effectiveSubscriptionUserAgent(customValue: String?, mode: AppMode): String =
    customValue
        ?.takeIf { mode == AppMode.ADVANCED && isValidSubscriptionUserAgent(it) }
        ?: defaultSubscriptionUserAgent()

private fun defaultSubscriptionUserAgent(): String = "Kaihi/${BuildConfig.VERSION_NAME}"

private const val MAX_SUBSCRIPTION_USER_AGENT_LENGTH = 256
private val PRINTABLE_ASCII_RANGE = 0x20..0x7E
