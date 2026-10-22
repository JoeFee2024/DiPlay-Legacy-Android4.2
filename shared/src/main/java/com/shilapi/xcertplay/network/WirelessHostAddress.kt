package com.shilapi.xcertplay.network

import android.os.Build
import com.shilapi.xcertplay.compat.realSdkInt
import java.net.Inet4Address
import java.net.Inet6Address
import java.io.File
import java.net.InetAddress
import java.net.NetworkInterface

/** NetworkInterface.getIndex is API 19; /sys/class/net provides the same value on 4.2. */
internal fun interfaceIndexOrZero(networkInterface: NetworkInterface): Int =
    if (realSdkInt() >= Build.VERSION_CODES.KITKAT) {
        runCatching { networkInterface.index }.getOrDefault(0)
    } else {
        runCatching {
            File("/sys/class/net/${networkInterface.name}/ifindex").readText().trim().toInt()
        }.getOrDefault(0)
    }

internal fun wirelessHostAddress(addresses: List<InetAddress>, interfaceIndex: Int, preferIpv4: Boolean = true): InetAddress? {
    val ipv4 = if (preferIpv4) addresses.filterIsInstance<Inet4Address>().firstOrNull {
        !it.isLoopbackAddress && !it.isLinkLocalAddress && !it.isAnyLocalAddress && !it.isMulticastAddress
    } else null
    val selected = ipv4 ?: HotspotAddressPolicy.select(addresses) ?: return null
    if (selected is Inet6Address && interfaceIndex > 0) {
        return Inet6Address.getByAddress(null, selected.address, interfaceIndex)
    }
    return selected
}

/** Station LAN discovery must cover IPv4 multicast as well as scoped link-local IPv6. */
internal fun existingWifiHostAddresses(addresses: List<InetAddress>, interfaceIndex: Int): List<InetAddress> {
    val ipv4 = addresses.firstOrNull {
        it is Inet4Address && !it.isLoopbackAddress && !it.isLinkLocalAddress &&
            !it.isAnyLocalAddress && !it.isMulticastAddress
    }
    val ipv6 = if (interfaceIndex > 0) {
        addresses.filterIsInstance<Inet6Address>().firstOrNull { it.isLinkLocalAddress }?.let {
            Inet6Address.getByAddress(null, it.address, interfaceIndex)
        }
    } else {
        null
    }
    return listOfNotNull(ipv4, ipv6)
}
