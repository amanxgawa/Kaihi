package app.kaihi.android.feature.importing.impl.ui.mvi

import androidx.compose.runtime.Stable
import app.kaihi.android.R
import app.kaihi.android.core.vpn.SubscriptionFetchFailure
import app.kaihi.android.feature.importing.impl.domain.ImportCoordinator
import app.kaihi.android.feature.importing.impl.domain.ImportOutcome
import app.kaihi.android.feature.importing.impl.domain.ImportPreparation
import app.kaihi.android.feature.importing.impl.domain.ImportPreview
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
    private var addJob: Job? = null
    private var preparedPreview: ImportPreview? = null
    private var prefillConsumed = false

    override suspend fun onIntent(intent: ImportContract.Intent) {
        when (intent) {
            is ImportContract.Intent.TextChanged -> reduce { copy(text = intent.text, errorMessage = null) }
            is ImportContract.Intent.Prefilled -> if (!prefillConsumed) {
                prefillConsumed = true
                reduce { copy(text = intent.text, errorMessage = null) }
                startImport()
            }
            ImportContract.Intent.ManualEntryRequested -> reduce { copy(isManualEntry = true) }
            is ImportContract.Intent.Pasted -> {
                reduce { copy(text = intent.text, errorMessage = null) }
                startImport()
            }
            ImportContract.Intent.ImportRequested -> startImport()
            ImportContract.Intent.AddRequested -> startAdd()
            is ImportContract.Intent.GroupToggled -> reduce {
                copy(
                    expandedGroups = if (intent.key in expandedGroups) {
                        expandedGroups - intent.key
                    } else {
                        expandedGroups + intent.key
                    },
                )
            }
            ImportContract.Intent.ShowExistingRequested -> state.value.alreadyAdded?.let {
                sendEffect(ImportContract.Effect.ShowExisting(it.firstServerId))
            }
            ImportContract.Intent.CancelRequested -> cancelImport()
            ImportContract.Intent.SetUpServerRequested -> sendEffect(ImportContract.Effect.SetUpServer)
        }
    }

    private fun startImport() {
        val text = state.value.text
        if (
            text.isBlank() || state.value.isImporting || state.value.isAdding ||
            state.value.preview != null || state.value.alreadyAdded != null
        ) return

        reduce { copy(isImporting = true, errorMessage = null) }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                when (val preparation = coordinator.prepare(text)) {
                    is ImportPreparation.Ready -> {
                        preparedPreview = preparation.preview
                        reduce {
                            copy(
                                isImporting = false,
                                preview = preparation.preview.toUi(System.currentTimeMillis()),
                                alreadyAdded = null,
                                expandedGroups = emptySet(),
                                errorMessage = null,
                            )
                        }
                    }
                    is ImportPreparation.AlreadyAdded -> {
                        preparedPreview = null
                        reduce {
                            copy(
                                isImporting = false,
                                preview = null,
                                alreadyAdded = preparation.toUi(),
                                expandedGroups = emptySet(),
                                errorMessage = null,
                            )
                        }
                    }
                    is ImportPreparation.FetchFailed -> reduce {
                        copy(isManualEntry = true, errorMessage = preparation.reason.toUiText())
                    }
                    is ImportPreparation.NothingImportable -> reduce {
                        copy(
                            isManualEntry = true,
                            errorMessage = UiText.Resource(
                                R.string.import_error_nothing_importable,
                            ),
                        )
                    }
                    ImportPreparation.InsecureUrl -> reduce {
                        copy(
                            isManualEntry = true,
                            errorMessage = UiText.Resource(R.string.import_error_use_https),
                        )
                    }
                    ImportPreparation.Unrecognized -> reduce {
                        copy(
                            isManualEntry = true,
                            errorMessage = UiText.Resource(R.string.import_error_unrecognized),
                        )
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                reduce {
                    copy(
                        isManualEntry = true,
                        errorMessage = UiText.Resource(R.string.import_error_unexpected),
                    )
                }
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

    private fun startAdd() {
        val preview = preparedPreview ?: return
        if (state.value.isAdding || state.value.isImporting) return

        reduce { copy(isAdding = true, errorMessage = null) }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            var succeeded = false
            try {
                when (val outcome = coordinator.add(preview)) {
                    is ImportOutcome.Success -> {
                        succeeded = true
                        preparedPreview = null
                        sendEffect(
                            ImportContract.Effect.ImportSucceeded(outcome.firstAddedServerId),
                        )
                    }
                    is ImportOutcome.AlreadyAdded -> showInputError(
                        UiText.Resource(R.string.import_error_already_added),
                    )
                    is ImportOutcome.NothingNew -> showInputError(
                        UiText.Resource(
                            if (outcome.counts.duplicates > 0) {
                                R.string.import_error_already_added
                            } else {
                                R.string.import_error_nothing_importable
                            },
                        ),
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                showInputError(UiText.Resource(R.string.import_error_unexpected))
            } finally {
                if (addJob === currentCoroutineContext()[Job]) {
                    addJob = null
                    if (!succeeded) reduce { copy(isAdding = false) }
                }
            }
        }
        addJob = job
        job.start()
    }

    private fun showInputError(error: UiText) {
        preparedPreview = null
        reduce {
            copy(
                preview = null,
                alreadyAdded = null,
                expandedGroups = emptySet(),
                isManualEntry = true,
                errorMessage = error,
            )
        }
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
