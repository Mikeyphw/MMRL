package com.dergoogler.mmrl.network

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetAddress

class NetworkRedirectHardeningTest {
    @Test fun publicHttpsRedirectIsAccepted() {
        assertEquals("https://github.com/o/r/releases/tag/v1", NetworkPolicy.requireSafeRedirect("https://github.com/o/r/releases/latest", "/o/r/releases/tag/v1"))
    }

    @Test(expected = IllegalArgumentException::class) fun loopbackHostIsRejected() {
        NetworkPolicy.requireSafeRemoteUrl("https://localhost/repository.json")
    }

    @Test(expected = IllegalArgumentException::class) fun privateIpv4IsRejected() {
        NetworkPolicy.requireSafeRemoteAddress(InetAddress.getByName("192.168.1.1"))
    }

    @Test(expected = IllegalArgumentException::class) fun linkLocalIpv4IsRejected() {
        NetworkPolicy.requireSafeRemoteAddress(InetAddress.getByName("169.254.1.1"))
    }

    @Test(expected = IllegalArgumentException::class) fun loopbackIpv6IsRejected() {
        NetworkPolicy.requireSafeRemoteAddress(InetAddress.getByName("::1"))
    }

    @Test(expected = IllegalArgumentException::class) fun uniqueLocalIpv6IsRejected() {
        NetworkPolicy.requireSafeRemoteAddress(InetAddress.getByName("fd00::1"))
    }

    @Test(expected = IllegalArgumentException::class) fun redirectDowngradeIsRejected() {
        NetworkPolicy.requireSafeRedirect("https://example.com/a", "http://example.com/b")
    }

    @Test(expected = IllegalArgumentException::class) fun carrierGradeNatIpv4IsRejected() {
        NetworkPolicy.requireSafeRemoteAddress(InetAddress.getByName("100.64.0.1"))
    }

    @Test(expected = IllegalArgumentException::class) fun zeroNetworkIpv4IsRejected() {
        NetworkPolicy.requireSafeRemoteAddress(InetAddress.getByName("0.1.2.3"))
    }

    @Test(expected = IllegalArgumentException::class) fun urlCredentialsAreRejected() {
        NetworkPolicy.requireSafeRemoteUrl("https://user:pass@example.com/repository.json")
    }

    @Test(expected = IllegalArgumentException::class) fun urlFragmentsAreRejected() {
        NetworkPolicy.requireSafeRemoteUrl("https://example.com/repository.json#fragment")
    }

    @Test(expected = IllegalArgumentException::class) fun redirectToPrivateLiteralIsRejected() {
        NetworkPolicy.requireSafeRedirect("https://example.com/a", "https://192.168.1.10/b")
    }

}
