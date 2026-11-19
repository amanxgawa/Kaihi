package app.kaihi.android.core.vpn

import app.kaihi.android.core.storage.InMemoryAppStorage
import app.kaihi.android.core.mode.AppMode
import app.kaihi.android.core.mode.AppModeRepository
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        val storage = InMemoryAppStorage()
        val modeRepository = AppModeRepository(storage)
        val vpnPreferences = VpnPreferences(storage).apply { setIpv6Enabled(true) }
        val source = TunnelSettingsSource(vpnPreferences, modeRepository)
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

}
