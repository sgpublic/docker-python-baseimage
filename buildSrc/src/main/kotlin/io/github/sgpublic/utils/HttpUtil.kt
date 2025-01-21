package io.github.sgpublic.utils

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse

private val HttpClient by lazy {
    java.net.http.HttpClient.newHttpClient()
}

fun NetJsonObject(url: String, headers: Map<String, String> = emptyMap(), converter: (String) -> String = { it }): JsonObject {
    val json = NetString(url, headers)
    return Gson.fromJson(converter(json), JsonObject::class.java)
            ?: throw IllegalStateException("Failed to parse json! content: $json")
}

fun NetJsonArray(url: String, headers: Map<String, String> = emptyMap(), converter: (String) -> String = { it }): JsonArray {
    val json = NetString(url, headers)
    return Gson.fromJson(converter(json), JsonArray::class.java)
            ?: throw IllegalStateException("Failed to parse json! content: $json")
}

fun NetString(url: String, headers: Map<String, String> = emptyMap()): String = try {
    NetResp(url, headers).body()
} catch (e: Throwable) {
    throw IllegalStateException("Failed to read remote resource.")
}

fun NetResp(url: String, headers: Map<String, String> = emptyMap()): HttpResponse<String> {
    val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .also {
                for ((key, value) in headers) {
                    it.header(key, value)
                }
            }
            .build()
    return HttpClient.send(
            request, HttpResponse.BodyHandlers.ofString()
    )
}