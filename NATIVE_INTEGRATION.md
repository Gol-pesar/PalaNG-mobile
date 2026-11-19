# Native Xray-core Integration Contract (`NATIVE_INTEGRATION.md`)

## 1. Why the Native Core Is Not Bundled in AI Studio
The Google AI Studio Android build container supports Kotlin/Java and Gradle Android builds, but does **not** include the Go toolchain (`gomobile`) or Android NDK C/C++ cross-compilers required to build `Xray-core` or `hev-socks5-tunnel` from source.

In strict adherence to production engineering rules:
- The app **never** simulates VPN connectivity, fake traffic counters, or fake `CONNECTED` states.
- When no real Xray-core binary is present, `NativeXrayBridge.detectAvailability()` returns `EngineAvailability.NotInstalled` and the UI displays `ENGINE_NOT_INSTALLED`.
- `XrayVpnService` refuses to establish a dead-end TUN interface when the engine is missing.

---

## 2. Supported Upstream Native Targets

`app/build.gradle.kts` is pre-configured to automatically include externally built binaries placed in:
- `app/libs/*.aar` / `app/libs/*.jar`
- `app/src/main/jniLibs/{arm64-v8a,armeabi-v7a,x86_64}/*.so`

### Option A: Official `XTLS/libXray` (Recommended)
Upstream repository: `https://github.com/XTLS/libXray`

Expected Java/Go binding class (`libXray.LibXray`):
- `LibXray.xrayVersion(): String`
- `LibXray.runXrayFromJSON(base64Request: String): String` (base64 JSON `{ "datDir": "...", "configJSON": "..." }`)
- `LibXray.stopXray(): String`
- `LibXray.testXray(base64Request: String): String`
- `LibXray.queryStats(base64Request: String): String`
- `LibXray.dialerController` / `ProtectSocket(fd: Int): Boolean` (delegated directly to `VpnService.protect(fd)`)

If using a SOCKS5 local inbound (`127.0.0.1:10808`), pair with `hev-socks5-tunnel` (see Option C) or use upstream's TUN fd runner if compiled into your `libXray` build.

### Option B: `2dust/AndroidLibXrayLite`
Upstream repository: `https://github.com/2dust/AndroidLibXrayLite`

Expected Java/Go binding classes (`libv2ray.Libv2ray`, `libv2ray.CoreController`, `libv2ray.CoreCallbackHandler`):
- `Libv2ray.initCoreEnv(envPath: String, key: String)`
- `Libv2ray.checkVersionX(): String`
- `Libv2ray.newCoreController(handler: CoreCallbackHandler): CoreController`
- `CoreController.startLoop(configJson: String)`
- `CoreController.stopLoop()`
- `CoreController.queryStats(tag: String, direct: String): Long`
- `CoreCallbackHandler`:
  - `startup(): Long`
  - `shutdown(): Long`
  - `onEmitStatus(code: Long, status: String?): Long`

### Option C: Native TUN-to-SOCKS5 Bridge (`hev-socks5-tunnel`)
Upstream repository: `https://github.com/heiher/hev-socks5-tunnel`

Expected JNI class (`hev.htproxy.TProxyService`) + `libhev-socks5-tunnel.so`:
- `TProxyService.TProxyStartService(configPath: String, fd: Int)`
- `TProxyService.TProxyStopService()`
- `TProxyService.TProxyGetStats(): LongArray` -> `[txPackets, txBytes, rxPackets, rxBytes]`

---

## 3. Kotlin Isolation Contract (`XrayEngineAdapter`)

All native calls are isolated behind `com.example.xrayclient.engine.XrayEngineAdapter`:

```kotlin
interface XrayEngineAdapter {
    fun detectAvailability(): EngineAvailability
    suspend fun initialize(appContext: Context, assetDir: File): Result<Unit>
    suspend fun validateConfig(xrayJson: String): Result<Unit>
    suspend fun start(config: XrayStartConfig, protectSocket: (Int) -> Boolean): Result<Unit>
    suspend fun stop(): Result<Unit>
    suspend fun restart(config: XrayStartConfig, protectSocket: (Int) -> Boolean): Result<Unit>
    fun queryTrafficStats(): EngineTrafficStats?
    fun getCoreVersion(): String?
    fun observeState(): StateFlow<EngineRuntimeState>
    fun observeLogs(): StateFlow<List<EngineDiagnosticLog>>
}
```

## 4. Integrated Native Binaries (Active)
1. `app/libs/libv2ray.aar` (`2dust/AndroidLibV2rayLite` v5.56.0 — `libv2ray.Libv2ray`, `CoreController`, `CoreCallbackHandler`, `go.Seq`, plus bundled `geoip.dat` & `geosite.dat` and `libgojni.so` across `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`).
2. `app/libs/hev-socks5-tunnel.aar` (`heiher/hev-socks5-tunnel` v2.20.0 — `hev.htproxy.TProxyService` + `libhev-socks5-tunnel.so` across all 4 ABIs).
3. `NativeXrayBridge` extracts `geoip.dat`/`geosite.dat` to `filesDir/xray_assets`, binds `Seq.setContext(...)` and `Libv2ray.initCoreEnv(...)`, starts `CoreController.startLoop(...)` listening on `127.0.0.1:10808`, writes `hev-socks5-tunnel.yml`, and attaches the TUN fd via `TProxyService.TProxyStartService(...)`.
4. `TunManager` excludes the host app package via `addDisallowedApplication(ownPackageName)` so outbound sockets opened by `libgojni.so` bypass the TUN route cleanly without loops.
5. Note: `AndroidLibV2rayLite` v5.56.0 wraps `v2fly/v2ray-core/v5`; `XrayConfigBuilder` uses `"tcp"` / `"tcpSettings"` for broad compatibility, and `NativeXrayBridge.validateConfig` explicitly rejects Xray-only `REALITY` / `XHTTP` / `HTTPUpgrade` / `xtls` flows rather than silently failing or faking connections.
