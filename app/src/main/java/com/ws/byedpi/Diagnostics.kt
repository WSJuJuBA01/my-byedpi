package com.ws.byedpi

import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

object Diagnostics {
    private const val TIMEOUT = 6000

    private val hosts = listOf(
        "discord.com",
        "gateway.discord.gg",
        "cdn.discordapp.com",
        "www.youtube.com",
        "t.me",
        "max.ru"
    )

    fun run(): String {
        val pool = Executors.newFixedThreadPool(hosts.size)
        try {
            val tasks = hosts.map { h ->
                Callable {
                    "$h\n  напрямую: ${check(h, false)}\n  ByeDPI:   ${check(h, true)}"
                }
            }
            return pool.invokeAll(tasks).joinToString("\n") { it.get() }
        } finally {
            pool.shutdown()
        }
    }

    private fun check(host: String, viaProxy: Boolean): String {
        val start = System.currentTimeMillis()
        var raw: Socket? = null
        return try {
            raw = if (viaProxy) {
                Socket(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 1080)))
            } else {
                Socket()
            }
            val addr = if (viaProxy) {
                InetSocketAddress.createUnresolved(host, 443)
            } else {
                InetSocketAddress(host, 443)
            }
            raw.connect(addr, TIMEOUT)
            raw.soTimeout = TIMEOUT
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val ssl = factory.createSocket(raw, host, 443, true) as SSLSocket
            ssl.startHandshake()
            "OK ${System.currentTimeMillis() - start} мс"
        } catch (e: Exception) {
            "FAIL (${e.javaClass.simpleName}) ${System.currentTimeMillis() - start} мс"
        } finally {
            try {
                raw?.close()
            } catch (_: Exception) {
            }
        }
    }
}
