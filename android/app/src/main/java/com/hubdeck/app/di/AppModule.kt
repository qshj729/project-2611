package com.hubdeck.app.di

import android.content.Context
import androidx.room.Room
import com.hubdeck.app.core.data.db.CategoryDao
import com.hubdeck.app.core.data.db.CredentialDao
import com.hubdeck.app.core.data.db.EndpointDao
import com.hubdeck.app.core.data.db.HealthDao
import com.hubdeck.app.core.data.db.HubDeckDatabase
import com.hubdeck.app.core.data.db.ServiceDao
import com.hubdeck.app.core.network.DefaultServiceProbe
import com.hubdeck.app.core.network.ServiceProbe
import com.hubdeck.app.core.security.CredentialStore
import com.hubdeck.app.core.tailscale.OkHttpTailscaleControlApiClient
import com.hubdeck.app.core.tailscale.OkHttpTailscaleLocalApiClient
import com.hubdeck.app.core.tailscale.TailscaleControlApiClient
import com.hubdeck.app.core.tailscale.TailscaleLocalApiClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideProxyConfigurator(): ProxyConfigurator = ProxyConfigurator()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HubDeckDatabase =
        Room.databaseBuilder(context, HubDeckDatabase::class.java, "hubdeck.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideServiceDao(db: HubDeckDatabase): ServiceDao = db.serviceDao()

    @Provides
    fun provideEndpointDao(db: HubDeckDatabase): EndpointDao = db.endpointDao()

    @Provides
    fun provideCategoryDao(db: HubDeckDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideHealthDao(db: HubDeckDatabase): HealthDao = db.healthDao()

    @Provides
    fun provideCredentialDao(db: HubDeckDatabase): CredentialDao = db.credentialDao()

    @Provides
    @Singleton
    fun provideCredentialStore(
        @ApplicationContext context: Context,
        credentialDao: CredentialDao,
    ): CredentialStore = CredentialStore(context, credentialDao)

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    @Provides
    @Singleton
    fun provideLocalApiClient(
        client: OkHttpClient,
        credentials: CredentialStore,
    ): TailscaleLocalApiClient = OkHttpTailscaleLocalApiClient(client, credentials)

    @Provides
    @Singleton
    fun provideControlApiClient(
        client: OkHttpClient,
        credentials: CredentialStore,
    ): TailscaleControlApiClient = OkHttpTailscaleControlApiClient(client, credentials)

    @Provides
    @Singleton
    fun provideServiceProbe(client: OkHttpClient): ServiceProbe = DefaultServiceProbe(client)
}
