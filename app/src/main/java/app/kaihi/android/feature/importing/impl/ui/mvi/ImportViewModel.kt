package app.kaihi.android.feature.importing.impl.ui.mvi

import androidx.compose.runtime.Stable
import app.kaihi.android.R
import app.kaihi.android.core.vpn.SubscriptionFetchFailure
import app.kaihi.android.feature.importing.impl.domain.ImportCoordinator
import app.kaihi.android.feature.importing.impl.domain.ImportOutcome
import app.kaihi.android.platform.ui.mvi.MviViewModel
import app.kaihi.android.platform.ui.util.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch

@Stable
@HiltViewModel
internal class ImportViewModel @Inject constructor(
    private val coordinator: ImportCoordinator,
) : MviViewModel<ImportContract.State, ImportContract.Intent, ImportContract.Effect>(ImportContract.State()) {

    private var importJob: Job? = null

    override suspend fun onIntent(intent: ImportContract.Intent) {
        when (intent) {
            is ImportContract.Intent.TextChanged -> reduce { copy(text = intent.text, errorMessage = null) }
            ImportContract.Intent.ImportRequested -> startImport()
            ImportContract.Intent.CancelRequested -> cancelImport()
            ImportContract.Intent.BackRequested -> {
                cancelImport()
                sendEffect(ImportContract.Effect.NavigateBack)
            }
        }
    }

    private fun startImport() {
        val text = state.value.text
        if (text.isBlank() || state.value.isImporting) return

        reduce { copy(isImporting = true, errorMessage = null) }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                when (val outcome = coordinator.import(text)) {
                    is ImportOutcome.Success -> sendEffect(
                        ImportContract.Effect.ImportSucceeded(outcome.counts, outcome.firstAddedServerId),
                    )
                    is ImportOutcome.AlreadyAdded -> reduce {
                        copy(errorMessage = UiText.Resource(R.string.import_error_already_added))
                    }
                    is ImportOutcome.FetchFailed -> reduce {
                        copy(errorMessage = outcome.reason.toUiText())
                    }
                    is ImportOutcome.NothingImportable -> reduce {
                        copy(
                            errorMessage = UiText.Resource(
                                R.string.import_error_nothing_importable,
                                outcome.counts.unsupported,
                                outcome.counts.invalid,
                            ),
                        )
                    }
                    ImportOutcome.InsecureUrl -> reduce {
                        copy(errorMessage = UiText.Resource(R.string.import_error_use_https))
                    }
                    ImportOutcome.Unrecognized -> reduce {
                        copy(errorMessage = UiText.Resource(R.string.import_error_unrecognized))
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                reduce { copy(errorMessage = UiText.Resource(R.string.import_error_unexpected)) }
            } finally {
                if (importJob === currentCoroutineContext()[Job]) {
                    importJob = null
                    reduce { copy(isImporting = false) }
                }
            }
        }
        importJob = job
        job.start()
    }

    private fun cancelImport() {
        importJob?.cancel()
        importJob = null
        reduce { copy(isImporting = false) }
    }

    private fun SubscriptionFetchFailure.toUiText(): UiText = UiText.Resource(
        when (this) {
            SubscriptionFetchFailure.INSECURE_URL -> R.string.import_fetch_error_insecure_url
            SubscriptionFetchFailure.NETWORK -> R.string.import_fetch_error_network
            SubscriptionFetchFailure.TIMEOUT -> R.string.import_fetch_error_timeout
            SubscriptionFetchFailure.HTTP_STATUS -> R.string.import_fetch_error_http_status
            SubscriptionFetchFailure.TOO_LARGE -> R.string.import_fetch_error_too_large
            SubscriptionFetchFailure.TOO_MANY_REDIRECTS -> R.string.import_fetch_error_redirects
            SubscriptionFetchFailure.UNSUPPORTED_FORMAT -> R.string.import_fetch_error_unsupported_format
            SubscriptionFetchFailure.EMPTY -> R.string.import_fetch_error_empty
        },
    )
}
