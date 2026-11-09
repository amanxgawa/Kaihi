package app.kaihi.android.feature.settings.impl.ui.screen

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kaihi.android.R
import app.kaihi.android.feature.settings.impl.domain.InstalledApp
import app.kaihi.android.feature.settings.impl.ui.mvi.ExcludedAppsContract
import app.kaihi.android.feature.settings.impl.ui.mvi.ExcludedAppsViewModel
import app.kaihi.android.platform.ui.icons.KaihiIcons
import app.kaihi.android.platform.ui.theme.size.dimens
import app.kaihi.android.platform.ui.util.CollectAsEffect
import app.kaihi.android.platform.ui.util.asString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ExcludedAppsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExcludedAppsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    viewModel.effect.CollectAsEffect { effect ->
        when (effect) {
            ExcludedAppsContract.Effect.NavigateBack -> onBack()
        }
    }

    ExcludedAppsContent(
        state = state,
        onBack = { viewModel.processIntent(ExcludedAppsContract.Intent.BackRequested) },
        onQueryChanged = { viewModel.processIntent(ExcludedAppsContract.Intent.QueryChanged(it)) },
        onAppToggled = { packageName, excluded ->
            viewModel.processIntent(ExcludedAppsContract.Intent.AppToggled(packageName, excluded))
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExcludedAppsContent(
    state: ExcludedAppsContract.State,
    onBack: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onAppToggled: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = MaterialTheme.dimens
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val apps = remember(state.apps, state.query) {
        state.apps.filter { app ->
            state.query.isBlank() || app.label.contains(state.query, ignoreCase = true) ||
                app.packageName.contains(state.query, ignoreCase = true)
        }
    }

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
                text = stringResource(R.string.settings_excluded_apps_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        SearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = state.query,
                    onQueryChange = onQueryChanged,
                    onSearch = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    },
                    expanded = false,
                    onExpandedChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.settings_excluded_apps_search)) },
                    leadingIcon = {
                        Icon(
                            imageVector = KaihiIcons.Outlined.Search,
                            contentDescription = null,
                        )
                    },
                    trailingIcon = if (state.query.isNotEmpty()) {
                        {
                            IconButton(onClick = { onQueryChanged("") }) {
                                Icon(
                                    imageVector = KaihiIcons.Outlined.Close,
                                    contentDescription = stringResource(
                                        R.string.settings_excluded_apps_clear_search,
                                    ),
                                )
                            }
                        }
                    } else {
                        null
                    },
                )
            },
            expanded = false,
            onExpandedChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.l, vertical = dimens.m),
        ) {}

        Text(
            text = stringResource(R.string.settings_excluded_apps_hint),
            modifier = Modifier.padding(horizontal = dimens.l),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when {
            state.isLoading -> Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            state.errorMessage != null -> Text(
                text = state.errorMessage.asString(),
                modifier = Modifier.weight(1f).fillMaxWidth().padding(dimens.l),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )

            apps.isEmpty() -> Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(dimens.l),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.settings_excluded_apps_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = dimens.l,
                    end = dimens.l,
                    bottom = dimens.l,
                ),
                verticalArrangement = Arrangement.spacedBy(dimens.s),
            ) {
                items(apps, key = InstalledApp::packageName) { app ->
                    ExcludedAppRow(
                        app = app,
                        checked = app.packageName in state.excludedPackages,
                        onCheckedChange = { onAppToggled(app.packageName, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExcludedAppRow(
    app: InstalledApp,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val dimens = MaterialTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = dimens.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.m),
    ) {
        InstalledAppIcon(
            packageName = app.packageName,
            label = app.label,
            modifier = Modifier.size(40.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun InstalledAppIcon(
    packageName: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val icon by produceState<ImageBitmap?>(initialValue = null, packageName) {
        value = withContext(Dispatchers.IO) {
            try {
                context.packageManager.getApplicationIcon(packageName).toBitmap().asImageBitmap()
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }
    }

    if (icon != null) {
        Image(
            bitmap = icon!!,
            contentDescription = null,
            modifier = modifier,
        )
    } else {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label.take(1).uppercase(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
