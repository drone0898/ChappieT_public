package kr.com.chappiet.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import kr.com.chappiet.BuildConfig
import kr.com.chappiet.data.remote.api.ChappieTFunctionService
import kr.com.chappiet.data.remote.api.DeepLApiService
import kr.com.chappiet.data.remote.api.DeepLPROApiService
import kr.com.chappiet.data.remote.api.PapagoApiService
import kr.com.chappiet.di.NetworkModule.Companion.NAVER_REQUEST_HEADER_CLIENT_ID
import kr.com.chappiet.di.NetworkModule.Companion.NAVER_REQUEST_HEADER_CLIENT_SECRET
import kr.com.chappiet.util.COMMON_TIMEOUT
import okhttp3.ConnectionPool
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.InetAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import javax.inject.Named


@Module
@InstallIn(SingletonComponent::class)
class NetworkModule {
    @Provides
    @Named(PAPAGO)
    fun providePapagoBaseUrl() = "https://openapi.naver.com/"

    @Provides
    @Named(DEEPL)
    fun provideDeepLBaseUrl2() = "https://api-free.deepl.com/"

    @Provides
    @Named(DEEPLPRO)
    fun provideBaseUrl2() = "https://api.deepl.com/"

    @Provides
    @Named(ChappieTFunction)
    fun provideChappieTBaseUrl() = BuildConfig.CHAPPIET_BASE_URL

    @Provides
    @Named(PAPAGO)
    fun providePapagoHeaderInterceptor(apiKeyProvider: ApiKeyProvider): Interceptor
            = AddPapagoHeaderInterceptor(apiKeyProvider)

    @Provides
    fun provideOkHttpClient(): OkHttpClient {
        return okHttpClientBuilder().build()
    }
    @Provides
    @Named(PAPAGO)
    fun providePapagoOkHttpClient(@Named(PAPAGO) headerInterceptor: Interceptor): OkHttpClient {
        return okHttpClientBuilder().apply {
            addInterceptor(headerInterceptor)
        }.build()
    }

    private fun okHttpClientBuilder(): OkHttpClient.Builder {
        val connectionTimeOut = (COMMON_TIMEOUT).toLong()
        val readTimeOut = (COMMON_TIMEOUT).toLong()
        val interceptor = HttpLoggingInterceptor()
        // 첫 요청이 느린 문제 해결하기
        // https://github.com/square/okhttp/issues/6486
        val bootstrapClient = OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .build()
        val dns = DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url("https://dns.google/dns-query".toHttpUrl())
            .bootstrapDnsHosts(InetAddress.getByName("8.8.4.4"), InetAddress.getByName("8.8.8.8"))
            .build()
        if (BuildConfig.DEBUG) {
            interceptor.level = HttpLoggingInterceptor.Level.BODY
            interceptor.redactHeader("Authorization")
            interceptor.redactHeader("Cookie")
        }else{
            interceptor.level = HttpLoggingInterceptor.Level.NONE
        }

        return OkHttpClient.Builder().apply {
            connectionPool(ConnectionPool(10,2,TimeUnit.MINUTES))
            addInterceptor(interceptor)
            dns(dns)
            proxy(Proxy.NO_PROXY)
            readTimeout(readTimeOut, TimeUnit.MILLISECONDS)
            connectTimeout(connectionTimeOut, TimeUnit.MILLISECONDS)
        }
    }


    @Provides
    fun provideKtorClient(okHttpClient: OkHttpClient): HttpClient {
        return HttpClient(OkHttp){
            engine {
                config{
                    followRedirects(true)
                }
                preconfigured = okHttpClient
            }
        }
    }

    @Provides
    @Named(PAPAGO)
    fun provideRetrofitPapago(@Named(PAPAGO) okHttpClient: OkHttpClient,
                              @Named(PAPAGO) baseUrl: String): Retrofit {
        return Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Named(DEEPL)
    fun provideRetrofitDeepL(okHttpClient: OkHttpClient,
                             @Named(DEEPL) baseUrl: String): Retrofit {
        return Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Named(DEEPLPRO)
    fun provideRetrofitDeepLPro(okHttpClient: OkHttpClient,
                             @Named(DEEPLPRO) baseUrl: String): Retrofit {
        return Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Named(ChappieTFunction)
    fun provideRetrofitChappieTFunction(okHttpClient: OkHttpClient,
                             @Named(ChappieTFunction) baseUrl: String): Retrofit {
        return Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    fun providePapagoService(@Named(PAPAGO) retrofit: Retrofit): PapagoApiService {
        return retrofit.create(PapagoApiService::class.java)
    }

    @Provides
    fun provideDeepLApiService(@Named(DEEPL) retrofit: Retrofit): DeepLApiService {
        return retrofit.create(DeepLApiService::class.java)
    }

    @Provides
    fun provideDeepLPROApiService(@Named(DEEPLPRO) retrofit: Retrofit): DeepLPROApiService {
        return retrofit.create(DeepLPROApiService::class.java)
    }

    @Provides
    fun provideChappieTApiService(@Named(ChappieTFunction) retrofit:Retrofit): ChappieTFunctionService {
        return retrofit.create(ChappieTFunctionService::class.java)
    }

    companion object {
        const val PAPAGO="papago"
        const val DEEPL="deepl"
        const val DEEPLPRO="deeplpro"
        const val KTOR="kotr"
        const val ChappieTFunction="ChappieTFunction"

        const val NAVER_REQUEST_HEADER_CLIENT_ID = "X-Naver-Client-Id"
        const val NAVER_REQUEST_HEADER_CLIENT_SECRET = "X-Naver-Client-Secret"

        const val DEEPL_REQUEST_AUTH_KEY = "DeepL-Auth-Key"
    }
}


class AddPapagoHeaderInterceptor constructor(
    private val apiKeyProvider: ApiKeyProvider
): Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder().apply {
//            addHeader(NAVER_REQUEST_HEADER_CLIENT_ID,apiKeyProvider.userNaverClientId)
//            addHeader(NAVER_REQUEST_HEADER_CLIENT_SECRET,apiKeyProvider.userNaverSecret)
        }
        return chain.proceed(builder.build())
    }
}