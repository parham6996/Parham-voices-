package com.example.data.remote

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.util.concurrent.TimeUnit

enum class ProxyProtocol(val id: String, val titleFa: String) {
    HTTP("HTTP", "HTTP / HTTPS"),
    SOCKS("SOCKS", "SOCKS5")
}

data class ProxySettings(
    val enabled: Boolean = false,
    val protocol: ProxyProtocol = ProxyProtocol.HTTP,
    val host: String = "",
    val port: Int = 10808,
    val username: String = "",
    val password: String = "",
    val customProxyListText: String = ""
)

data class ProxyBenchmarkCandidate(
    val protocol: ProxyProtocol,
    val host: String,
    val port: Int,
    val username: String = "",
    val password: String = "",
    val sourceLabel: String,
    val latencyMs: Long,
    val reachable: Boolean
) {
    fun displayEndpoint(): String = "${protocol.id}://$host:$port"
}

data class AutoProxyDiscoveryReport(
    val bestCandidate: ProxyBenchmarkCandidate?,
    val testedCandidates: List<ProxyBenchmarkCandidate>,
    val summaryMessage: String
)

object ProxyConfigStore {
    private const val PREFS_NAME = "avaclone_proxy_prefs"
    private const val KEY_ENABLED = "proxy_enabled"
    private const val KEY_PROTOCOL = "proxy_protocol"
    private const val KEY_HOST = "proxy_host"
    private const val KEY_PORT = "proxy_port"
    private const val KEY_USERNAME = "proxy_username"
    private const val KEY_PASSWORD = "proxy_password"
    private const val KEY_CUSTOM_LIST = "proxy_custom_list"

    fun load(context: Context): ProxySettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val protoStr = prefs.getString(KEY_PROTOCOL, ProxyProtocol.HTTP.id) ?: ProxyProtocol.HTTP.id
        val protocol = ProxyProtocol.entries.firstOrNull { it.id == protoStr } ?: ProxyProtocol.HTTP
        return ProxySettings(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            protocol = protocol,
            host = prefs.getString(KEY_HOST, "") ?: "",
            port = prefs.getInt(KEY_PORT, 10808),
            username = prefs.getString(KEY_USERNAME, "") ?: "",
            password = prefs.getString(KEY_PASSWORD, "") ?: "",
            customProxyListText = prefs.getString(KEY_CUSTOM_LIST, "") ?: ""
        )
    }

    fun save(context: Context, settings: ProxySettings) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putString(KEY_PROTOCOL, settings.protocol.id)
            .putString(KEY_HOST, settings.host.trim())
            .putInt(KEY_PORT, settings.port.coerceIn(1, 65535))
            .putString(KEY_USERNAME, settings.username.trim())
            .putString(KEY_PASSWORD, settings.password)
            .putString(KEY_CUSTOM_LIST, settings.customProxyListText)
            .apply()
    }

    /**
     * Direct OkHttp client that bypasses app proxy settings (ideal for Iranian domestic AI gateways like AvalAI/Liara/GapGPT).
     */
    fun buildDirectOkHttpClient(readTimeoutSec: Long = 60L): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    /**
     * App-only Split-Tunnel OkHttp client: routes ONLY ParHam VoiCe requests through configured HTTP/SOCKS5 proxy
     * without changing system-wide phone VPN.
     */
    fun buildOkHttpClient(context: Context, readTimeoutSec: Long = 90L): OkHttpClient {
        val settings = load(context)
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val builder = OkHttpClient.Builder()
            .connectTimeout(22, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .addInterceptor(logging)

        if (settings.enabled && settings.host.isNotBlank() && settings.port in 1..65535) {
            applyProxyToBuilder(
                builder = builder,
                protocol = settings.protocol,
                host = settings.host.trim(),
                port = settings.port,
                username = settings.username.trim(),
                password = settings.password
            )
        }
        return builder.build()
    }

    private fun applyProxyToBuilder(
        builder: OkHttpClient.Builder,
        protocol: ProxyProtocol,
        host: String,
        port: Int,
        username: String,
        password: String
    ) {
        val javaProxyType = when (protocol) {
            ProxyProtocol.HTTP -> Proxy.Type.HTTP
            ProxyProtocol.SOCKS -> Proxy.Type.SOCKS
        }
        val proxy = Proxy(javaProxyType, InetSocketAddress(host, port))
        builder.proxy(proxy)
        if (username.isNotBlank() && protocol == ProxyProtocol.HTTP) {
            val credential = Credentials.basic(username, password)
            builder.proxyAuthenticator(Authenticator { _, response ->
                response.request.newBuilder()
                    .header("Proxy-Authorization", credential)
                    .build()
            })
        }
    }

    suspend fun testConnection(context: Context): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val settings = load(context)
        if (settings.enabled && settings.host.isBlank()) {
            return@withContext false to "آدرس سرور پروکسی (Host) وارد نشده است."
        }
        val startMs = System.currentTimeMillis()
        try {
            val client = buildOkHttpClient(context, readTimeoutSec = 14L)
            val request = Request.Builder()
                .url("https://api.elevenlabs.io/v1/models")
                .head()
                .build()
            client.newCall(request).execute().use { resp ->
                val elapsed = System.currentTimeMillis() - startMs
                val mode = if (settings.enabled) {
                    "تونل اختصاصی برنامه (${settings.protocol.id}://${settings.host}:${settings.port})"
                } else {
                    "اتصال مستقیم"
                }
                return@withContext true to "اتصال موفق ($mode) • پاسخ سرور: ${resp.code} • پینگ: ${elapsed}ms"
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val mode = if (settings.enabled) "پروکسی ${settings.host}:${settings.port}" else "شبکه مستقیم"
            return@withContext false to "عدم اتصال از طریق $mode: ${e.localizedMessage ?: "خطای شبکه"}"
        }
    }

    /**
     * Parses user-supplied proxy lines supporting:
     * - host:port
     * - http://host:port  or  https://host:port
     * - socks5://host:port or socks://host:port
     * - host:port:username:password
     */
    fun parseProxyList(rawText: String, defaultSource: String = "لیست کاربر"): List<ProxyBenchmarkCandidate> {
        if (rawText.isBlank()) return emptyList()
        val parsed = mutableListOf<ProxyBenchmarkCandidate>()
        val seen = HashSet<String>()

        rawText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("//") }
            .forEach { rawLine ->
                var line = rawLine
                var protocol = ProxyProtocol.HTTP
                when {
                    line.startsWith("socks5://", ignoreCase = true) -> {
                        protocol = ProxyProtocol.SOCKS
                        line = line.substringAfter("://")
                    }
                    line.startsWith("socks://", ignoreCase = true) -> {
                        protocol = ProxyProtocol.SOCKS
                        line = line.substringAfter("://")
                    }
                    line.startsWith("https://", ignoreCase = true) -> {
                        protocol = ProxyProtocol.HTTP
                        line = line.substringAfter("://")
                    }
                    line.startsWith("http://", ignoreCase = true) -> {
                        protocol = ProxyProtocol.HTTP
                        line = line.substringAfter("://")
                    }
                }
                line = line.substringBefore("/").trim()

                var user = ""
                var pass = ""
                if (line.contains("@")) {
                    val authPart = line.substringBefore("@")
                    line = line.substringAfter("@")
                    user = authPart.substringBefore(":")
                    pass = authPart.substringAfter(":", "")
                }

                val parts = line.split(":")
                if (parts.size >= 2) {
                    val host = parts[0].trim()
                    val port = parts[1].trim().toIntOrNull()
                    if (parts.size >= 4 && user.isBlank()) {
                        user = parts[2].trim()
                        pass = parts[3].trim()
                    }
                    if (host.isNotBlank() && port != null && port in 1..65535) {
                        val key = "${protocol.id}:$host:$port"
                        if (seen.add(key)) {
                            parsed += ProxyBenchmarkCandidate(
                                protocol = protocol,
                                host = host,
                                port = port,
                                username = user,
                                password = pass,
                                sourceLabel = defaultSource,
                                latencyMs = Long.MAX_VALUE,
                                reachable = false
                            )
                        }
                    }
                }
            }
        return parsed
    }

    /**
     * Discovers working proxies strictly for ParHam VoiCe by:
     * 1) Scanning local tunnel ports (v2rayNG / Nekobox / Hiddify / Clash on 127.0.0.1)
     * 2) Testing user's custom proxy list
     * 3) Optionally fetching public free HTTP/SOCKS lists from ProxyScrape
     * Benchmarks real HTTPS reachability in parallel and binds the fastest working proxy.
     */
    suspend fun autoDiscoverAndSelectBestProxy(
        context: Context,
        customListText: String,
        includePublicPool: Boolean
    ): AutoProxyDiscoveryReport = withContext(Dispatchers.IO) {
        val candidates = mutableListOf<ProxyBenchmarkCandidate>()
        val seen = HashSet<String>()

        fun addCandidate(c: ProxyBenchmarkCandidate) {
            val k = "${c.protocol.id}:${c.host}:${c.port}"
            if (seen.add(k)) candidates += c
        }

        // 1. Local split-tunnel client ports on device (v2rayNG, Nekobox, Hiddify, Clash)
        val localPorts = listOf(
            10809 to ProxyProtocol.HTTP,
            10808 to ProxyProtocol.SOCKS,
            1080 to ProxyProtocol.SOCKS,
            2080 to ProxyProtocol.SOCKS,
            7890 to ProxyProtocol.HTTP,
            8080 to ProxyProtocol.HTTP
        )
        for ((port, proto) in localPorts) {
            if (isTcpPortOpen("127.0.0.1", port, timeoutMs = 260)) {
                addCandidate(
                    ProxyBenchmarkCandidate(
                        protocol = proto,
                        host = "127.0.0.1",
                        port = port,
                        sourceLabel = "کلاینت محلی گوشی",
                        latencyMs = Long.MAX_VALUE,
                        reachable = false
                    )
                )
            }
        }

        // 2. User-provided proxy list
        parseProxyList(customListText, "لیست شخصی شما").forEach(::addCandidate)

        // 3. Optional public proxy pool fetch (real open proxy feed)
        if (includePublicPool) {
            fetchPublicProxyFeedCandidates(limit = 16).forEach(::addCandidate)
        }

        if (candidates.isEmpty()) {
            return@withContext AutoProxyDiscoveryReport(
                bestCandidate = null,
                testedCandidates = emptyList(),
                summaryMessage = "هیچ پورت محلی فعالی (10808/10809/2080) یا پروکسی معتبری پیدا نشد. می‌توانید لیست پروکسی وارد کنید یا از درگاه ایرانی بدون فیلترشکن (AvalAI/Liara/GapGPT) استفاده کنید."
            )
        }

        val probeResults = coroutineScope {
            candidates.take(28).map { cand ->
                async(Dispatchers.IO) {
                    benchmarkProxyCandidate(cand)
                }
            }.awaitAll()
        }

        val sorted = probeResults.sortedWith(
            compareByDescending<ProxyBenchmarkCandidate> { it.reachable }
                .thenBy { it.latencyMs }
        )
        val best = sorted.firstOrNull { it.reachable }

        if (best != null) {
            val existing = load(context)
            save(
                context,
                existing.copy(
                    enabled = true,
                    protocol = best.protocol,
                    host = best.host,
                    port = best.port,
                    username = best.username,
                    password = best.password,
                    customProxyListText = customListText
                )
            )
            AutoProxyDiscoveryReport(
                bestCandidate = best,
                testedCandidates = sorted,
                summaryMessage = "بهترین پروکسی فقط برای ParHam VoiCe فعال شد: ${best.displayEndpoint()} (${best.sourceLabel} • ${best.latencyMs}ms)"
            )
        } else {
            AutoProxyDiscoveryReport(
                bestCandidate = null,
                testedCandidates = sorted,
                summaryMessage = "${sorted.size} پروکسی بررسی شد اما هیچ‌کدام به سرور مقصد متصل نشدند. لیست جدید وارد کنید یا کلاینت پروکسی محلی را روی حالت Proxy-Only بگذارید."
            )
        }
    }

    private fun isTcpPortOpen(host: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { sock ->
                sock.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun fetchPublicProxyFeedCandidates(limit: Int): List<ProxyBenchmarkCandidate> {
        val result = mutableListOf<ProxyBenchmarkCandidate>()
        val client = buildDirectOkHttpClient(readTimeoutSec = 8L)
        val feeds = listOf(
            "https://api.proxyscrape.com/v2/?request=displayproxies&protocol=http&timeout=4500&country=all&ssl=yes&anonymity=all" to ProxyProtocol.HTTP,
            "https://api.proxyscrape.com/v2/?request=displayproxies&protocol=socks5&timeout=4500&country=all" to ProxyProtocol.SOCKS
        )
        for ((url, proto) in feeds) {
            try {
                val req = Request.Builder().url(url).get().build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        val perFeed = parseProxyList(body, "فهرست عمومی آنلاین")
                            .map { it.copy(protocol = proto) }
                            .take(limit / 2)
                        result += perFeed
                    }
                }
            } catch (_: Exception) {
            }
        }
        return result
    }

    private fun benchmarkProxyCandidate(candidate: ProxyBenchmarkCandidate): ProxyBenchmarkCandidate {
        val startMs = System.currentTimeMillis()
        return try {
            val builder = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(6, TimeUnit.SECONDS)
            applyProxyToBuilder(
                builder = builder,
                protocol = candidate.protocol,
                host = candidate.host,
                port = candidate.port,
                username = candidate.username,
                password = candidate.password
            )
            val req = Request.Builder()
                .url("https://api.elevenlabs.io/v1/models")
                .head()
                .build()
            builder.build().newCall(req).execute().use { resp ->
                val elapsed = System.currentTimeMillis() - startMs
                val ok = resp.code in 200..499
                candidate.copy(reachable = ok, latencyMs = if (ok) elapsed else Long.MAX_VALUE)
            }
        } catch (_: Exception) {
            candidate.copy(reachable = false, latencyMs = Long.MAX_VALUE)
        }
    }
}
