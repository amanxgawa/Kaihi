package app.kaihi.android.feature.dashboard.impl.ui.mvi

import app.kaihi.android.R
import app.kaihi.android.core.vpn.XrayFailure
import app.kaihi.android.platform.ui.util.UiText

internal fun failureMessage(failure: XrayFailure?): UiText? = when (failure) {
    XrayFailure.NO_WORKING_FINGERPRINT -> UiText.Resource(R.string.dashboard_error_no_working_fingerprint)
    XrayFailure.OTHER, null -> null
}
