package com.rzh.valo.data

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ReleaseInfo(
    /** 不含 v 前缀的版本号，如 1.0.3 */
    val version: String,
    /** Release 页面地址 */
    val url: String,
)

/** 语义化比较点分版本号：[candidate] 比 [current] 新时返回 true；容忍 v 前缀与位数不足 */
fun isNewerVersion(current: String, candidate: String): Boolean {
    fun parse(v: String) = v.trim().removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
    val a = parse(current)
    val b = parse(candidate)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return y > x
    }
    return false
}

/** 通过 GitHub Releases 检查新版本 */
class UpdateChecker(private val repo: String = "rzh0504/valo") {

    private val json = Json { ignoreUnknownKeys = true }

    private val client: HttpClient = HttpClient {
        config()
    }

    /** 引擎由各平台 source set 提供（Android OkHttp / iOS Darwin），公共配置集中在这里 */
    private fun HttpClientConfig<*>.config() {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 15_000
        }
    }

    /** 最新正式 Release；网络失败或响应异常时抛出异常，由调用方转为失败态 */
    suspend fun latestRelease(): ReleaseInfo {
        val resp = client.get("https://api.github.com/repos/$repo/releases/latest")
        if (!resp.status.isSuccess()) throw ApiException("HTTP ${resp.status.value}")
        val obj = json.parseToJsonElement(resp.bodyAsText()).jsonObject
        val tag = obj["tag_name"]?.jsonPrimitive?.content ?: throw ApiException("响应缺少 tag_name")
        val url = obj["html_url"]?.jsonPrimitive?.content ?: "https://github.com/$repo/releases/latest"
        return ReleaseInfo(version = tag.removePrefix("v"), url = url)
    }
}
