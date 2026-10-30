package com.smouha.call

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {
    fun localIpv4(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (ni in interfaces) {
                if (!ni.isUp || ni.isLoopback) continue
                val addrs = ni.inetAddresses
                while (addrs.hasMoreElements()) {
                    val a = addrs.nextElement()
                    if (a is Inet4Address && !a.isLoopbackAddress) {
                        val host = a.hostAddress ?: continue
                        // prefer private LAN ranges
                        if (host.startsWith("192.168.") ||
                            host.startsWith("10.") ||
                            host.startsWith("172.")
                        ) {
                            return host
                        }
                    }
                }
            }
            // fallback any non-loopback IPv4
            val interfaces2 = NetworkInterface.getNetworkInterfaces() ?: return null
            for (ni in interfaces2) {
                if (!ni.isUp || ni.isLoopback) continue
                val addrs = ni.inetAddresses
                while (addrs.hasMoreElements()) {
                    val a = addrs.nextElement()
                    if (a is Inet4Address && !a.isLoopbackAddress) {
                        return a.hostAddress
                    }
                }
            }
        } catch (_: Exception) {
        }
        return null
    }
}
