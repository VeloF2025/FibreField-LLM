package com.fibreflow.core.network

import android.content.Context
import com.fibreflow.core.network.api.AuthApi
import com.fibreflow.core.network.interceptors.AuthInterceptor
import com.fibreflow.core.network.interceptors.LoggingInterceptor
import com.fibreflow.core.network.interceptors.NetworkSecurityInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt module for network dependencies
 * Provides Retrofit, OkHttp, and API service instances
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        loggingInterceptor: LoggingInterceptor,
        securityInterceptor: NetworkSecurityInterceptor,
        certificatePinner: CertificatePinner
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(securityInterceptor)
            .addInterceptor(loggingInterceptor)
            .certificatePinner(certificatePinner)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(com.fibreflow.core.network.BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi {
        return retrofit.create(AuthApi::class.java)
    }

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): LoggingInterceptor {
        return LoggingInterceptor()
    }

    @Provides
    @Singleton
    fun provideNetworkSecurityInterceptor(
        @ApplicationContext context: Context
    ): NetworkSecurityInterceptor {
        return NetworkSecurityInterceptor(context)
    }

    @Provides
    @Singleton
    fun provideCertificatePinner(): CertificatePinner {
        return CertificatePinner.Builder()
            .add("api.fibreflow.com", com.fibreflow.core.network.BuildConfig.CERTIFICATE_PIN)
            .build()
    }
}