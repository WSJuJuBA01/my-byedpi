package com.ws.byedpi

import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class Target(val name: String, val host: String, val control: Boolean = false)

class CheckResult(val ok: Boolean, val ms: Long, val error: String?)

class TestRow(val target: Target) {
    @Volatile
    var direct: CheckResult? = null

    @Volatile
    var proxy: CheckResult? = null
}

object Diagnostics {
    private const val TIMEOUT = 5000
    private const val SOCKS_PORT = 1080

    // Список можно менять: имя для показа и домен для проверки
    val targets: List<Target> = listOf(
        Target("YouTube", "www.youtube.com"),
        Target("Discord", "discord.com"),
        Target("Discord Gateway", "gateway.discord.gg"),
        Target("Discord CDN", "cdn.discordapp.com"),
        Target("Instagram", "www.instagram.com"),
        Target("Facebook", "www.facebook.com"),
        Target("X (Twitter)", "x.com"),
        Target("LinkedIn", "www.linkedin.com"),
        Target("Speedtest", "www.speedtest.net"),
        Target("SoundCloud", "soundcloud.com"),
        Target("Signal", "signal.org"),
        Target("Viber", "www.viber.com"),
        Target("Wikipedia (контроль)", "www.wikipedia.org", control = true)
    )

    /** Проверка: TLS-рукопожатие с SNI = host, напрямую или через SOCKS ByeDPI */
    fun check(host: String, viaProxy: Boolean): CheckResult {
        val start = System.currentTimeMillis()
        var raw: Socket? = null
        return try {
            raw = if (viaProxy) {
                Socket(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", SOCKS_PORT)))
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
            CheckResult(true, System.currentTimeMillis() - start, null)
        } catch (e: Exception) {
            CheckResult(false, System.currentTimeMillis() - start, describe(e))
        } finally {
            try {
                raw?.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun describe(e: Exception): String {
        val msg = (e.message ?: "").lowercase()
        return when {
            e is SocketTimeoutException -> "таймаут"
            e is UnknownHostException -> "DNS не нашёл"
            msg.contains("reset") -> "сброс соединения"
            e is ConnectException || msg.contains("refused") -> "нет соединения"
            e is SSLHandshakeException -> "ошибка TLS"
            e is SSLException -> "обрыв TLS"
            e is SocketException -> "обрыв"
            else -> e.javaClass.simpleName
        }
    }

    /** Заглушка для совместимости со старым кодом, тест теперь на отдельном экране */
    fun run(): String = "Тест перенесён на отдельный экран"
}
