package app.kaihi.android.feature.settings.impl.ui.mvi

import androidx.compose.runtime.Stable
import app.kaihi.android.R
import app.kaihi.android.core.vpn.VpnPreferences
import app.kaihi.android.core.vpn.effectiveSubscriptionUserAgent
import app.kaihi.android.core.vpn.isValidSubscriptionUserAgent
import app.kaihi.android.platform.mode.AppMode
import app.kaihi.android.platform.mode.AppModeRepository
import app.kaihi.android.platform.ui.mvi.MviViewModel
import app.kaihi.android.platform.ui.util.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Stable
@HiltViewModel
internal class SettingsViewModel @Inject constructor(
    private val appModeRepository: AppModeRepository,
    private val vpnPreferences: VpnPreferences,
) : MviViewModel<SettingsContract.State, SettingsContract.Intent, SettingsContract.Effect>(
    SettingsContract.State(
        mode = appModeRepository.mode.value,
        ipv6Enabled = vpnPreferences.ipv6Enabled.value,
        excludedAppsCount = vpnPreferences.excludedPackages.value.size,
        subscriptionUserAgent = vpnPreferences.subscriptionUserAgent.value,
        effectiveSubscriptionUserAgent = effectiveSubscriptionUserAgent(
            customValue = vpnPreferences.subscriptionUserAgent.value,
            mode = appModeRepository.mode.value,
        ),
    )
) {

    init {
        appModeRepository.mode
            .onEach { mode ->
                reduce {
                    copy(
                        mode = mode,
                        effectiveSubscriptionUserAgent = effectiveSubscriptionUserAgent(
                            customValue = subscriptionUserAgent,
                            mode = mode,
                        ),
                        isSubscriptionUserAgentDialogVisible =
                            isSubscriptionUserAgentDialogVisible && mode == AppMode.ADVANCED,
                    )
                }
            }
            .launchIn(scope)

        vpnPreferences.ipv6Enabled
            .onEach { enabled -> reduce { copy(ipv6Enabled = enabled) } }
            .launchIn(scope)

        vpnPreferences.excludedPackages
            .onEach { packages -> reduce { copy(excludedAppsCount = packages.size) } }
            .launchIn(scope)

        vpnPreferences.subscriptionUserAgent
            .onEach { value ->
                reduce {
                    copy(
                        subscriptionUserAgent = value,
                        effectiveSubscriptionUserAgent = effectiveSubscriptionUserAgent(
                            customValue = value,
                            mode = mode,
                        ),
                    )
                }
            }
            .launchIn(scope)
    }

    override suspend fun onIntent(intent: SettingsContract.Intent) {
        when (intent) {
            is SettingsContract.Intent.AdvancedModeToggled -> if (intent.enabled) {
                sendEffect(SettingsContract.Effect.NavigateToAdvancedModeIntro)
            } else {
                appModeRepository.setMode(AppMode.NORMAL)
            }

            is SettingsContract.Intent.Ipv6Toggled -> vpnPreferences.setIpv6Enabled(intent.enabled)
            SettingsContract.Intent.ExcludedAppsRequested -> sendEffect(SettingsContract.Effect.NavigateToExcludedApps)
            SettingsContract.Intent.SubscriptionUserAgentRequested -> if (state.value.mode == AppMode.ADVANCED) {
                reduce {
                    copy(
                        isSubscriptionUserAgentDialogVisible = true,
                        subscriptionUserAgentDraft = subscriptionUserAgent.orEmpty(),
                        subscriptionUserAgentError = null,
                    )
                }
            }
            is SettingsContract.Intent.SubscriptionUserAgentChanged -> {
                reduce {
                    copy(
                        subscriptionUserAgentDraft = intent.value,
                        subscriptionUserAgentError = intent.value
                            .takeUnless(::isValidSubscriptionUserAgent)
                            ?.let { UiText.Resource(R.string.settings_subscription_user_agent_invalid) },
                    )
                }
            }
            SettingsContract.Intent.SubscriptionUserAgentSaved -> {
                val draft = state.value.subscriptionUserAgentDraft
                if (!isValidSubscriptionUserAgent(draft)) {
                    reduce {
                        copy(
                            subscriptionUserAgentError =
                                UiText.Resource(R.string.settings_subscription_user_agent_invalid),
                        )
                    }
                } else if (vpnPreferences.setSubscriptionUserAgent(draft)) {
                    reduce {
                        copy(
                            isSubscriptionUserAgentDialogVisible = false,
                            subscriptionUserAgentError = null,
                        )
                    }
                }
            }
            SettingsContract.Intent.SubscriptionUserAgentReset -> {
                vpnPreferences.setSubscriptionUserAgent(null)
                reduce {
                    copy(
                        isSubscriptionUserAgentDialogVisible = false,
                        subscriptionUserAgentDraft = "",
                        subscriptionUserAgentError = null,
                    )
                }
            }
            SettingsContract.Intent.SubscriptionUserAgentDialogDismissed -> reduce {
                copy(
                    isSubscriptionUserAgentDialogVisible = false,
                    subscriptionUserAgentError = null,
                )
            }
            SettingsContract.Intent.ViewLogsRequested -> sendEffect(SettingsContract.Effect.NavigateToLogs)
            SettingsContract.Intent.BackRequested -> sendEffect(SettingsContract.Effect.NavigateBack)
        }
    }
}
