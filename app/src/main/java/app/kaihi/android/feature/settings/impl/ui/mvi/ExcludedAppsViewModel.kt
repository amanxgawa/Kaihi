package app.kaihi.android.feature.settings.impl.ui.mvi

import androidx.compose.runtime.Stable
import app.kaihi.android.R
import app.kaihi.android.core.vpn.VpnPreferences
import app.kaihi.android.feature.settings.impl.domain.InstalledAppsRepository
import app.kaihi.android.platform.ui.mvi.MviViewModel
import app.kaihi.android.platform.ui.util.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber

@Stable
@HiltViewModel
internal class ExcludedAppsViewModel @Inject constructor(
    private val installedAppsRepository: InstalledAppsRepository,
    private val vpnPreferences: VpnPreferences,
) : MviViewModel<ExcludedAppsContract.State, ExcludedAppsContract.Intent, ExcludedAppsContract.Effect>(
    ExcludedAppsContract.State(excludedPackages = vpnPreferences.excludedPackages.value),
) {

    init {
        vpnPreferences.excludedPackages
            .onEach { packages -> reduce { copy(excludedPackages = packages) } }
            .launchIn(scope)

        scope.launch {
            try {
                val apps = installedAppsRepository.listLaunchableApps()
                val excludedPackages = vpnPreferences.excludedPackages.value
                val orderedApps = apps.partition { it.packageName in excludedPackages }
                    .let { (excluded, included) -> excluded + included }
                reduce { copy(apps = orderedApps, isLoading = false) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                Timber.e(error, "Failed to load launchable apps")
                reduce {
                    copy(
                        isLoading = false,
                        errorMessage = UiText.Resource(R.string.settings_excluded_apps_load_error),
                    )
                }
            }
        }
    }

    override suspend fun onIntent(intent: ExcludedAppsContract.Intent) {
        when (intent) {
            is ExcludedAppsContract.Intent.QueryChanged -> reduce { copy(query = intent.query) }
            is ExcludedAppsContract.Intent.AppToggled -> {
                val packages = vpnPreferences.excludedPackages.value
                vpnPreferences.setExcludedPackages(
                    if (intent.excluded) packages + intent.packageName else packages - intent.packageName,
                )
            }
            ExcludedAppsContract.Intent.BackRequested -> sendEffect(ExcludedAppsContract.Effect.NavigateBack)
        }
    }
}
