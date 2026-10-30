package com.moblized.nutritrack.data

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout

/** One HTTP client for Mealie, Open Food Facts and update checks (OkHttp on Android, NSURLSession on iPhone). */
internal val http: HttpClient by lazy {
    HttpClient {
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 45_000
        }
        expectSuccess = false
    }
}
