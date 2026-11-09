package app.kaihi.android.feature.settings.impl.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import app.kaihi.android.feature.settings.impl.domain.InstalledApp
import app.kaihi.android.feature.settings.impl.domain.InstalledAppsRepository
import app.kaihi.android.platform.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class PackageManagerInstalledAppsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : InstalledAppsRepository {

    override suspend fun listLaunchableApps(): List<InstalledApp> = withContext(ioDispatcher) {
        val packageManager = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val installedApps = packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
            .mapNotNull { resolveInfo ->
                val applicationInfo = resolveInfo.activityInfo?.applicationInfo ?: return@mapNotNull null
                val packageName = applicationInfo.packageName
                if (packageName == context.packageName) return@mapNotNull null
                InstalledApp(
                    packageName = packageName,
                    label = packageManager.getApplicationLabel(applicationInfo).toString(),
                )
            }
            .distinctBy(InstalledApp::packageName)
        val collator = java.text.Collator.getInstance()

        installedApps.sortedWith { left, right ->
            collator.compare(left.label, right.label).takeIf { it != 0 }
                ?: left.packageName.compareTo(right.packageName)
        }
    }
}
