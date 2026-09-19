package com.dergoogler.mmrl.network

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NetworkHardeningSourceContractTest {
    @Test fun dnsAndRedirectsRevalidateDestinations() {
        val dns = File("src/main/kotlin/com/dergoogler/mmrl/network/DnsResolver.kt").readText()
        val utils = File("src/main/kotlin/com/dergoogler/mmrl/network/NetworkUtils.kt").readText()
        assertTrue(dns.contains("requireSafeRemoteAddress"))
        assertTrue(utils.contains("addNetworkInterceptor"))
        assertTrue(utils.contains("requireSafeRedirect"))
        assertTrue(utils.contains("requireSafeRemoteUrl"))
    }

    @Test fun manualGithubRedirectIsValidated() {
        val source = File("src/main/kotlin/com/dergoogler/mmrl/repository/RepositorySourceLoader.kt").readText()
        assertTrue(source.contains("requireSafeRedirect"))
        assertTrue(source.contains("GitHub latest-release redirect left github.com"))
    }
}
