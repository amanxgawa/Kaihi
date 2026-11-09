package app.kaihi.android.feature.settings.impl.domain

interface InstalledAppsRepository {
    suspend fun listLaunchableApps(): List<InstalledApp>
}
