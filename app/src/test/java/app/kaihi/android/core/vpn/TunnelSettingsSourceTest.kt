package app.kaihi.android.core.vpn

import app.kaihi.android.core.vpn.internal.RepositoryTunnelSettingsSource
import app.kaihi.android.platform.mode.AppMode
import app.kaihi.android.platform.mode.AppModeRepository
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TunnelSettingsSourceTest {

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `source combines mode IPv6 and excluded apps reactively`() = runTest {
        val modeRepository = FakeAppModeRepository(AppMode.NORMAL)
        val vpnPreferences = FakeVpnPreferences(initialIpv6Enabled = true)
        val source = RepositoryTunnelSettingsSource(vpnPreferences, modeRepository)
        val emissions = async(start = CoroutineStart.UNDISPATCHED) {
            source.settings.take(4).toList()
        }
        runCurrent()

        modeRepository.setMode(AppMode.ADVANCED)
        runCurrent()
        vpnPreferences.setIpv6Enabled(false)
        runCurrent()
        vpnPreferences.setExcludedPackages(setOf("org.example.bank_app"))
        runCurrent()

        assertEquals(
            listOf(
                TunnelSettings(advancedMode = false, ipv6Enabled = true),
                TunnelSettings(advancedMode = true, ipv6Enabled = true),
                TunnelSettings(advancedMode = true, ipv6Enabled = false),
                TunnelSettings(
                    advancedMode = true,
                    ipv6Enabled = false,
                    excludedPackages = setOf("org.example.bank_app"),
                ),
            ),
            emissions.await(),
        )
        assertEquals(
            TunnelSettings(
                advancedMode = true,
                ipv6Enabled = false,
                excludedPackages = setOf("org.example.bank_app"),
            ),
            source.current(),
        )
    }

    private class FakeAppModeRepository(initialMode: AppMode) : AppModeRepository {
        private val mutableMode = MutableStateFlow(initialMode)
        override val mode: StateFlow<AppMode> = mutableMode.asStateFlow()

        override fun setMode(mode: AppMode) {
            mutableMode.value = mode
        }
    }

    private class FakeVpnPreferences(initialIpv6Enabled: Boolean) : VpnPreferences {
        private val mutableIpv6Enabled = MutableStateFlow(initialIpv6Enabled)
        private val mutableExcludedPackages = MutableStateFlow(emptySet<String>())
        override val ipv6Enabled: StateFlow<Boolean> = mutableIpv6Enabled.asStateFlow()
        override val excludedPackages: StateFlow<Set<String>> = mutableExcludedPackages.asStateFlow()

        override fun setIpv6Enabled(enabled: Boolean) {
            mutableIpv6Enabled.value = enabled
        }

        override fun setExcludedPackages(packages: Set<String>) {
            mutableExcludedPackages.value = packages
        }

    }
}
