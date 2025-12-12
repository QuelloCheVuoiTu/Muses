package it.unisannio.muses.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * WARNING: This class creates an OkHttpClient that trusts all SSL certificates and
 * disables hostname verification. This is UNSAFE and should only be used for
 * development or testing with self-signed certificates. Do NOT ship this to production.
 */
object RetrofitInstance {
    private const val BASE_URL = "https://muses.services.ding.unisannio.it/"

    // Token provider: should return the current bearer token or null if absent.
    // Callers can set this to integrate with `AuthTokenManager` at runtime.
    @Volatile
    var tokenProvider: (() -> String?)? = null

    private val unsafeClient: OkHttpClient by lazy {
        try {
            // Create a trust manager that does not validate certificate chains
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                    // Trust all client certs
                }

                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                    // Trust all server certs
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            // Install the all-trusting trust manager
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())
            val sslSocketFactory: SSLSocketFactory = sslContext.socketFactory

            // Build OkHttp client that uses the all-trusting trust manager and hostname verifier
            val builder = OkHttpClient.Builder()

            // Interceptor that adds Authorization header only when a token is available
            val authInterceptor = okhttp3.Interceptor { chain ->
                val original = chain.request()
                val token = tokenProvider?.invoke()

                val requestBuilder = original.newBuilder()
                if (!token.isNullOrEmpty()) {
                    requestBuilder.header("Authorization", "Bearer $token")
                }

                val request = requestBuilder.build()
                chain.proceed(request)
            }

            builder.addInterceptor(authInterceptor)
            builder.sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
            builder.hostnameVerifier(HostnameVerifier { _, _ -> true })
            
            // Set longer timeouts for chatbot responses (up to 20 seconds)
            builder.connectTimeout(10, TimeUnit.SECONDS)
            builder.readTimeout(20, TimeUnit.SECONDS)
            builder.writeTimeout(10, TimeUnit.SECONDS)
            
            builder.build()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    // Use the unsafe client unconditionally. The user requested accepting a self-signed
    // certificate without validation. This is insecure and should only be used for
    // development/testing.
    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(unsafeClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
