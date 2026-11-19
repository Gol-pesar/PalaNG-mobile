package com.example.xrayclient

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.example.xrayclient.data.models.AppSettings
import com.example.xrayclient.data.models.PerAppMode
import com.example.xrayclient.domain.managers.QRCodeManager
import com.example.xrayclient.engine.EngineAvailability
import com.example.xrayclient.engine.EngineDiagnosticLog
import com.example.xrayclient.engine.EngineNotInstalledException
import com.example.xrayclient.engine.EngineRuntimeState
import com.example.xrayclient.engine.EngineTrafficStats
import com.example.xrayclient.engine.NativeXrayBridge
import com.example.xrayclient.engine.XrayEngineAdapter
import com.example.xrayclient.engine.XrayStartConfig
import com.example.xrayclient.vpn.ConnectionState
import com.example.xrayclient.vpn.TunManager
import com.example.xrayclient.vpn.VpnStateHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TunAndEngineContractTest {

    /**
     * Strictly test-only fake adapter used to verify state transitions and interface contract
     * without ever shipping fake connectivity in production.
     */
    private class TestOnlyFakeXrayEngineAdapter : XrayEngineAdapter {
        private val state = MutableStateFlow(EngineRuntimeState.IDLE)
        private val logs = MutableStateFlow<List<EngineDiagnosticLog>>(emptyList())

        override fun detectAvailability() = EngineAvailability.Ready(
            backendName = "TestFake",
            coreVersion = "test-0.0.0",
            supportsDirectTunFd = true,
            requiresTun2SocksBridge = false
        )

        override suspend fun initialize(appContext: Context, assetDir: File) = Result.success(Unit)
        override suspend fun validateConfig(xrayJson: String) = Result.success(Unit)
        override suspend fun start(config: XrayStartConfig, protectSocket: (Int) -> Boolean): Result<Unit> {
            state.value = EngineRuntimeState.RUNNING
            return Result.success(Unit)
        }
        override suspend fun stop(): Result<Unit> {
            state.value = EngineRuntimeState.IDLE
            return Result.success(Unit)
        }
        override suspend fun restart(config: XrayStartConfig, protectSocket: (Int) -> Boolean) =
            start(config, protectSocket)
        override fun queryTrafficStats() = EngineTrafficStats(uplinkBytes = 100L, downlinkBytes = 200L)
        override fun getCoreVersion() = "test-0.0.0"
        override fun observeState(): StateFlow<EngineRuntimeState> = state.asStateFlow()
        override fun observeLogs(): StateFlow<List<EngineDiagnosticLog>> = logs.asStateFlow()
    }

    @Test
    fun `libv2ray and hev-socks5-tunnel AAR classes are bundled with expected signatures`() {
        val libv2rayCls = Class.forName("libv2ray.Libv2ray", false, this::class.java.classLoader)
        assertTrue(libv2rayCls.getMethod("checkVersionX") != null)
        assertTrue(libv2rayCls.getMethod("initCoreEnv", String::class.java, String::class.java) != null)
        assertTrue(
            libv2rayCls.getMethod(
                "measureOutboundDelay",
                String::class.java,
                String::class.java
            ) != null
        )
        val callbackCls = Class.forName("libv2ray.CoreCallbackHandler", false, this::class.java.classLoader)
        assertTrue(libv2rayCls.getMethod("newCoreController", callbackCls) != null)

        val controllerCls = Class.forName("libv2ray.CoreController", false, this::class.java.classLoader)
        assertTrue(controllerCls.getMethod("startLoop", String::class.java) != null)
        assertTrue(controllerCls.getMethod("stopLoop") != null)
        assertTrue(controllerCls.getMethod("queryStats", String::class.java, String::class.java) != null)

        val tproxyCls = Class.forName("hev.htproxy.TProxyService", false, this::class.java.classLoader)
        assertTrue(tproxyCls.getMethod("TProxyStartService", String::class.java, Int::class.javaPrimitiveType) != null)
        assertTrue(tproxyCls.getMethod("TProxyStopService") != null)
        assertTrue(tproxyCls.getMethod("TProxyIsRunning") != null)
        assertTrue(tproxyCls.getMethod("TProxyGetStats") != null)
    }

    @Test
    fun `production NativeXrayBridge reports NotInstalled safely on host JVM where ELF native libs cannot load`() = runTest {
        val bridge = NativeXrayBridge()
        val availability = bridge.detectAvailability()
        assertTrue(availability is EngineAvailability.NotInstalled)

        val startRes = bridge.start(
            config = XrayStartConfig(configJson = "{}", tunFd = 10, assetDirPath = "/tmp"),
            protectSocket = { true }
        )
        assertTrue(startRes.isFailure)
        assertTrue(startRes.exceptionOrNull() is EngineNotInstalledException)
    }

    @Test
    fun `TunManager builds correct IPv4 IPv6 DNS and per-app specs`() {
        val settings = AppSettings(
            ipv4Enabled = true,
            ipv6Enabled = true,
            primaryDns = "1.1.1.1",
            secondaryDns = "2606:4700:4700::1111",
            perAppMode = PerAppMode.BYPASS_SELECTED,
            perAppPackages = setOf("com.whatsapp")
        )
        val spec = TunManager.buildRouteSpec(settings, ownPackageName = "com.aistudio.xrayclient.mrvpnx")
        assertTrue(spec.enableIpv4)
        assertTrue(spec.enableIpv6)
        assertEquals(listOf("1.1.1.1", "2606:4700:4700::1111"), spec.dnsServers)
        assertTrue("com.aistudio.xrayclient.mrvpnx" in spec.disallowedPackages)
        assertTrue("com.whatsapp" in spec.disallowedPackages)

        val allowSettings = settings.copy(
            ipv6Enabled = false,
            perAppMode = PerAppMode.ALLOW_SELECTED,
            perAppPackages = setOf("org.telegram.messenger")
        )
        val allowSpec = TunManager.buildRouteSpec(allowSettings, ownPackageName = "com.aistudio.xrayclient.mrvpnx")
        assertFalse(allowSpec.enableIpv6)
        assertEquals(setOf("org.telegram.messenger"), allowSpec.allowedPackages)
        assertEquals(listOf("1.1.1.1"), allowSpec.dnsServers)
    }

    @Test
    fun `VpnStateHolder transitions accurately`() {
        VpnStateHolder.setState(ConnectionState.DISCONNECTED)
        assertEquals(ConnectionState.DISCONNECTED, VpnStateHolder.snapshot.value.state)

        VpnStateHolder.setState(ConnectionState.REQUESTING_PERMISSION)
        assertEquals(ConnectionState.REQUESTING_PERMISSION, VpnStateHolder.snapshot.value.state)

        VpnStateHolder.setState(ConnectionState.ENGINE_NOT_INSTALLED, errorMessage = "missing")
        assertEquals(ConnectionState.ENGINE_NOT_INSTALLED, VpnStateHolder.snapshot.value.state)
        assertEquals("missing", VpnStateHolder.snapshot.value.errorMessage)
    }

    @Test
    fun `QR code generation and decoding round-trip`() {
        val sampleUri = "vless://11111111-2222-3333-4444-555555555555@example.com:443?security=tls#QRTest"
        val bitmap: Bitmap? = QRCodeManager.generateQrBitmap(sampleUri, 320)
        assertTrue(bitmap != null)
        val decoded = QRCodeManager.decodeFromBitmap(bitmap!!)
        assertEquals(sampleUri, decoded)
    }

    @Test
    fun `test-only fake engine adapter lifecycle transitions`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fake = TestOnlyFakeXrayEngineAdapter()
        assertEquals(EngineRuntimeState.IDLE, fake.observeState().value)
        fake.initialize(context, context.filesDir)
        fake.start(XrayStartConfig("{}", tunFd = 42, assetDirPath = context.filesDir.absolutePath)) { true }
        assertEquals(EngineRuntimeState.RUNNING, fake.observeState().value)
        fake.stop()
        assertEquals(EngineRuntimeState.IDLE, fake.observeState().value)
    }
}
