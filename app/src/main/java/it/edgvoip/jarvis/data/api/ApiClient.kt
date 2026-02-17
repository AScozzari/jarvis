package it.edgvoip.jarvis.data.api

import it.edgvoip.jarvis.BuildConfig
import it.edgvoip.jarvis.data.model.RefreshRequest
import it.edgvoip.jarvis.data.model.RefreshResponse
import it.edgvoip.jarvis.data.model.ApiResponse
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    /** Base URL per le chiamate: da login (apiBaseUrl) se presente, altrimenti da BuildConfig. */
    fun getBaseUrl(tokenManager: TokenManager): String {
        val fromLogin = tokenManager.getApiBaseUrl()?.takeIf { it.isNotBlank() }
        val base = fromLogin?.let { if (it.endsWith("/")) it else "$it/" } ?: BuildConfig.API_BASE_URL
        return base
    }

    /** Invalida la cache di Retrofit/OkHttp (da chiamare dopo login per usare la nuova apiBaseUrl). */
    fun invalidate() {
        retrofit = null
        okHttpClient = null
    }

    private var tokenManager: TokenManager? = null
    private var retrofit: Retrofit? = null
    private var okHttpClient: OkHttpClient? = null

    fun init(tokenManager: TokenManager) {
        this.tokenManager = tokenManager
    }

    fun getOkHttpClient(tokenManager: TokenManager): OkHttpClient {
        if (okHttpClient == null || this.tokenManager !== tokenManager) {
            this.tokenManager = tokenManager
            okHttpClient = buildOkHttpClient(tokenManager)
        }
        return okHttpClient!!
    }

    fun getRetrofit(tokenManager: TokenManager): Retrofit {
        if (retrofit == null || this.tokenManager !== tokenManager) {
            this.tokenManager = tokenManager
            retrofit = buildRetrofit(tokenManager)
        }
        return retrofit!!
    }

    fun getApi(tokenManager: TokenManager): JarvisApi {
        return getRetrofit(tokenManager).create(JarvisApi::class.java)
    }

    private fun buildOkHttpClient(tokenManager: TokenManager): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val apiBaseUrlInterceptor = Interceptor { chain ->
            val request = chain.request()
            val apiBase = tokenManager.getApiBaseUrl()?.takeIf { it.isNotBlank() }
            val newRequest = if (apiBase != null) {
                val path = request.url.encodedPath
                val pathWithoutSlug = path.replace(Regex("^/api/[^/]+"), "")
                val query = request.url.query
                val pathAndQuery = pathWithoutSlug + if (query != null) "?$query" else ""
                val newUrl = "${apiBase.trimEnd('/')}$pathAndQuery".toHttpUrlOrNull()
                if (newUrl != null) {
                    request.newBuilder().url(newUrl).build()
                } else {
                    request
                }
            } else {
                request
            }
            chain.proceed(newRequest)
        }

        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val token = tokenManager.getToken()
            val requestBuilder = original.newBuilder()
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")

            if (token != null) {
                requestBuilder.header("Authorization", "Bearer $token")
            }

            chain.proceed(requestBuilder.build())
        }

        val tokenAuthenticator = object : Authenticator {
            override fun authenticate(route: Route?, response: Response): Request? {
                if (response.code == 401) {
                    val refreshToken = tokenManager.getRefreshToken() ?: return null
                    val slug = tokenManager.getTenantSlug() ?: return null

                    synchronized(this) {
                        val currentToken = tokenManager.getToken()
                        val requestToken = response.request.header("Authorization")
                            ?.removePrefix("Bearer ")

                        if (currentToken != null && currentToken != requestToken) {
                            return response.request.newBuilder()
                                .header("Authorization", "Bearer $currentToken")
                                .build()
                        }

                        val refreshResult = try {
                            val refreshApi = buildRefreshRetrofit(tokenManager)
                                .create(JarvisApi::class.java)
                            runBlocking {
                                 refreshApi.refreshToken(
                                    slug,
                                    RefreshRequest(refreshToken)
                                )
                            }
                        } catch (e: Exception) {
                            null
                        }

                        if (refreshResult != null && refreshResult.isSuccessful) {
                            val body = refreshResult.body()
                            if (body?.success == true && body.data != null) {
                                tokenManager.saveToken(body.data.token)
                                tokenManager.saveRefreshToken(body.data.refreshToken)

                                return response.request.newBuilder()
                                    .header("Authorization", "Bearer ${body.data.token}")
                                    .build()
                            }
                        }

                        tokenManager.clearAll()
                        return null
                    }
                }
                return null
            }
        }

        val builder = OkHttpClient.Builder()
            .addInterceptor(apiBaseUrlInterceptor)
            .addInterceptor(authInterceptor)
            .authenticator(tokenAuthenticator)
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(loggingInterceptor)
        }
        return builder
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun buildRetrofit(tokenManager: TokenManager): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(buildOkHttpClient(tokenManager))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private fun buildRefreshRetrofit(tokenManager: TokenManager): Retrofit {
        val apiBaseUrlInterceptor = Interceptor { chain ->
            val request = chain.request()
            val apiBase = tokenManager.getApiBaseUrl()?.takeIf { it.isNotBlank() }
            val newRequest = if (apiBase != null) {
                val path = request.url.encodedPath
                val pathWithoutSlug = path.replace(Regex("^/api/[^/]+"), "")
                val query = request.url.query
                val pathAndQuery = pathWithoutSlug + if (query != null) "?$query" else ""
                val newUrl = "${apiBase.trimEnd('/')}$pathAndQuery".toHttpUrlOrNull()
                if (newUrl != null) {
                    request.newBuilder().url(newUrl).build()
                } else {
                    request
                }
            } else {
                request
            }
            chain.proceed(newRequest)
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(apiBaseUrlInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
