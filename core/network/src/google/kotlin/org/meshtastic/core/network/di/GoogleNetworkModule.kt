/*
 * Copyright (c) 2025 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.meshtastic.core.network.di

import android.content.Context
import com.datadog.android.okhttp.DatadogEventListener
import com.datadog.android.okhttp.DatadogInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.jensklingenberg.ktorfit.Ktorfit
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.meshtastic.core.network.BuildConfig
import org.meshtastic.core.network.service.ApiService
import org.meshtastic.core.network.service.createApiService
import java.io.File
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class GoogleNetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient = OkHttpClient.Builder()
        .cache(
            cache =
            Cache(
                directory = File(context.applicationContext.cacheDir, "http_cache"),
                maxSize = 50L * 1024L * 1024L, // 50 MiB
            ),
        )
        .addInterceptor(
            interceptor =
            HttpLoggingInterceptor().apply {
                if (BuildConfig.DEBUG) {
                    setLevel(HttpLoggingInterceptor.Level.BODY)
                }
            },
        )
        .addInterceptor(interceptor = DatadogInterceptor.Builder(tracedHosts = listOf("meshtastic.org")).build())
        .eventListenerFactory(eventListenerFactory = DatadogEventListener.Factory())
        .build()

    @Provides
    @Singleton
    fun provideHttpClient(okHttpClient: OkHttpClient): HttpClient = HttpClient(engineFactory = OkHttp) {
        engine { preconfigured = okHttpClient }

        install(plugin = ContentNegotiation) {
            json(
                Json {
                    isLenient = true
                    ignoreUnknownKeys = true
                },
            )
        }
    }

    @Provides
    @Singleton
    fun provideApiService(httpClient: HttpClient): ApiService {
        val ktorfit = Ktorfit.Builder().baseUrl(url = "https://api.meshtastic.org/").httpClient(httpClient).build()
        return ktorfit.createApiService()
    }
}
