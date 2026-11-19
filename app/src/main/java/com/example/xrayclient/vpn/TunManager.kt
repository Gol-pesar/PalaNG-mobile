package com.example.xrayclient.vpn

import android.content.Context
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.example.xrayclient.data.models.AppSettings
import com.example.xrayclient.data.models.PerAppMode
import com.example.xrayclient.utils.NetworkUtils

data class TunRouteSpec(
    val mtu: Int = 1500,
    val ipv4Address: String = "172.19.0.1",
    val ipv4Prefix: Int = 30,
    val ipv6Address: String = "fd00::1",
    val ipv6Prefix: Int = 126,
    val enableIpv4: Boolean,
    val enableIpv6: Boolean,
    val dnsServers: List<String>,
    val allowedPackages: Set<String>,
    val disallowedPackages: Set<String>
)

/**
 * Configures and establishes the Android TUN interface via [VpnService.Builder]
 * supporting IPv4, IPv6, configurable DNS leak prevention, and per-app routing.
 */
object TunManager {

    const val DEFAULT_MTU = 1500
    const val TUN_IPV4_ADDRESS = "172.19.0.1"
    const val TUN_IPV4_PREFIX = 30
    const val TUN_IPV6_ADDRESS = "fd00::1"
    const val TUN_IPV6_PREFIX = 126

    fun buildRouteSpec(settings: AppSettings, ownPackageName: String): TunRouteSpec {
        val enableIpv4 = settings.ipv4Enabled || !settings.ipv6Enabled
        val dnsList = buildList {
            val p = settings.primaryDns.trim()
            val s = settings.secondaryDns.trim()
            if (isAllowedDnsAddress(p, enableIpv4, settings.ipv6Enabled)) add(p)
            if (isAllowedDnsAddress(s, enableIpv4, settings.ipv6Enabled) && s != p) add(s)
            if (isEmpty()) {
                if (enableIpv4) add("1.1.1.1") else add("2606:4700:4700::1111")
            }
        }

        val allowed = mutableSetOf<String>()
        val disallowed = mutableSetOf<String>()

        when (settings.perAppMode) {
            PerAppMode.ALL_APPS -> {
                disallowed += ownPackageName
            }
            PerAppMode.BYPASS_SELECTED -> {
                disallowed += ownPackageName
                disallowed += settings.perAppPackages
            }
            PerAppMode.ALLOW_SELECTED -> {
                val filtered = settings.perAppPackages.filter { it != ownPackageName }
                if (filtered.isNotEmpty()) {
                    allowed += filtered
                } else {
                    // Fallback to bypassing own package if user left allowlist empty
                    disallowed += ownPackageName
                }
            }
        }

        return TunRouteSpec(
            mtu = DEFAULT_MTU,
            ipv4Address = TUN_IPV4_ADDRESS,
            ipv4Prefix = TUN_IPV4_PREFIX,
            ipv6Address = TUN_IPV6_ADDRESS,
            ipv6Prefix = TUN_IPV6_PREFIX,
            enableIpv4 = enableIpv4,
            enableIpv6 = settings.ipv6Enabled,
            dnsServers = dnsList,
            allowedPackages = allowed,
            disallowedPackages = disallowed
        )
    }

    fun establishTun(
        service: VpnService,
        context: Context,
        settings: AppSettings,
        sessionName: String
    ): ParcelFileDescriptor? {
        val spec = buildRouteSpec(settings, context.packageName)
        val builder = service.Builder()
            .setSession(sessionName)
            .setMtu(spec.mtu)
            .setBlocking(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        if (spec.enableIpv4) {
            builder.addAddress(spec.ipv4Address, spec.ipv4Prefix)
            builder.addRoute("0.0.0.0", 0)
        }

        if (spec.enableIpv6) {
            builder.addAddress(spec.ipv6Address, spec.ipv6Prefix)
            builder.addRoute("::", 0)
        }

        spec.dnsServers.forEach { dns ->
            runCatching { builder.addDnsServer(dns) }
        }

        val pm = context.packageManager
        spec.allowedPackages.forEach { pkg ->
            if (isPackageInstalled(pm, pkg)) {
                runCatching { builder.addAllowedApplication(pkg) }
            }
        }
        spec.disallowedPackages.forEach { pkg ->
            if (isPackageInstalled(pm, pkg)) {
                runCatching { builder.addDisallowedApplication(pkg) }
            }
        }

        return builder.establish()
    }

    private fun isAllowedDnsAddress(dns: String, ipv4: Boolean, ipv6: Boolean): Boolean {
        if (dns.isBlank()) return false
        return (ipv4 && NetworkUtils.isValidIpv4(dns)) ||
            (ipv6 && NetworkUtils.isValidIpv6Literal(dns))
    }

    private fun isPackageInstalled(pm: PackageManager, pkg: String): Boolean = try {
        pm.getPackageInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
