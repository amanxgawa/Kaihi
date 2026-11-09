package app.kaihi.android.feature.settings.impl.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kaihi.android.R
import app.kaihi.android.core.vpn.effectiveSubscriptionUserAgent
import app.kaihi.android.core.vpn.isValidSubscriptionUserAgent
import app.kaihi.android.feature.settings.impl.ui.mvi.SettingsContract
import app.kaihi.android.feature.settings.impl.ui.mvi.SettingsViewModel
import app.kaihi.android.feature.settings.impl.ui.screen.components.SettingsDangerZoneSection
import app.kaihi.android.feature.settings.impl.ui.screen.components.SettingsGeneralSection
import app.kaihi.android.feature.settings.impl.ui.screen.components.SettingsNetworkSection
import app.kaihi.android.feature.settings.impl.ui.screen.components.SettingsSubscriptionsSection
import app.kaihi.android.feature.settings.impl.ui.screen.components.SubscriptionUserAgentDialog
import app.kaihi.android.platform.mode.AppMode
import app.kaihi.android.platform.ui.icons.KaihiIcons
import app.kaihi.android.platform.ui.theme.size.dimens
import app.kaihi.android.platform.ui.util.CollectAsEffect

@Composable
internal fun SettingsScreen(
    onNavigateToLogs: () -> Unit,
    onNavigateToAdvancedModeIntro: () -> Unit,
    onNavigateToExcludedApps: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    viewModel.effect.CollectAsEffect { effect ->
        when (effect) {
            SettingsContract.Effect.NavigateToAdvancedModeIntro -> onNavigateToAdvancedModeIntro()
            SettingsContract.Effect.NavigateToExcludedApps -> onNavigateToExcludedApps()
            SettingsContract.Effect.NavigateToLogs -> onNavigateToLogs()
            SettingsContract.Effect.NavigateBack -> onNavigateBack()
        }
    }

    SettingsContent(
        state = state,
        onBack = { viewModel.processIntent(SettingsContract.Intent.BackRequested) },
        onViewLogsClick = { viewModel.processIntent(SettingsContract.Intent.ViewLogsRequested) },
        onAdvancedModeToggled = { enabled ->
            viewModel.processIntent(SettingsContract.Intent.AdvancedModeToggled(enabled))
        },
        onIpv6Toggled = { enabled ->
            viewModel.processIntent(SettingsContract.Intent.Ipv6Toggled(enabled))
        },
        onExcludedAppsClick = {
            viewModel.processIntent(SettingsContract.Intent.ExcludedAppsRequested)
        },
        onSubscriptionUserAgentClick = {
            viewModel.processIntent(SettingsContract.Intent.SubscriptionUserAgentRequested)
        },
        onSubscriptionUserAgentChanged = { value ->
            viewModel.processIntent(SettingsContract.Intent.SubscriptionUserAgentChanged(value))
        },
        onSubscriptionUserAgentSave = {
            viewModel.processIntent(SettingsContract.Intent.SubscriptionUserAgentSaved)
        },
        onSubscriptionUserAgentReset = {
            viewModel.processIntent(SettingsContract.Intent.SubscriptionUserAgentReset)
        },
        onSubscriptionUserAgentDismiss = {
            viewModel.processIntent(SettingsContract.Intent.SubscriptionUserAgentDialogDismissed)
        },
        modifier = modifier,
    )
}

@Composable
private fun SettingsContent(
    state: SettingsContract.State,
    onBack: () -> Unit,
    onViewLogsClick: () -> Unit,
    onAdvancedModeToggled: (Boolean) -> Unit,
    onIpv6Toggled: (Boolean) -> Unit,
    onExcludedAppsClick: () -> Unit,
    onSubscriptionUserAgentClick: () -> Unit,
    onSubscriptionUserAgentChanged: (String) -> Unit,
    onSubscriptionUserAgentSave: () -> Unit,
    onSubscriptionUserAgentReset: () -> Unit,
    onSubscriptionUserAgentDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = MaterialTheme.dimens
    val advancedModeEnabled = state.mode == AppMode.ADVANCED

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = dimens.m, end = dimens.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = KaihiIcons.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.settings_excluded_apps_back),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = stringResource(id = R.string.settings_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(dimens.l),
            verticalArrangement = Arrangement.spacedBy(dimens.l),
        ) {
            SettingsGeneralSection(onViewLogsClick = onViewLogsClick)

            SettingsNetworkSection(
                excludedAppsCount = state.excludedAppsCount,
                onExcludedAppsClick = onExcludedAppsClick,
                ipv6Enabled = state.ipv6Enabled,
                onIpv6Toggled = onIpv6Toggled,
                showIpv6 = advancedModeEnabled,
            )

            if (advancedModeEnabled) {
                SettingsSubscriptionsSection(
                    effectiveUserAgent = state.effectiveSubscriptionUserAgent,
                    onUserAgentClick = onSubscriptionUserAgentClick,
                )
            }

            SettingsDangerZoneSection(
                advancedModeEnabled = advancedModeEnabled,
                onAdvancedModeToggled = onAdvancedModeToggled,
            )
        }
    }

    if (state.isSubscriptionUserAgentDialogVisible) {
        SubscriptionUserAgentDialog(
            draft = state.subscriptionUserAgentDraft,
            defaultUserAgent = effectiveSubscriptionUserAgent(null, AppMode.NORMAL),
            error = state.subscriptionUserAgentError,
            saveEnabled = isValidSubscriptionUserAgent(state.subscriptionUserAgentDraft),
            onDraftChanged = onSubscriptionUserAgentChanged,
            onSave = onSubscriptionUserAgentSave,
            onReset = onSubscriptionUserAgentReset,
            onDismiss = onSubscriptionUserAgentDismiss,
        )
    }
}
