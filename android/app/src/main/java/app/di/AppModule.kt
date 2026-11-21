package app.di

import android.content.Context
import androidx.room.Room
import app.core.data.db.CategoryDao
import app.core.data.db.CredentialDao
import app.core.data.db.EndpointDao
import app.core.data.db.HealthDao
import app.core.data.db.HubDeckDatabase
import app.core.data.db.ServiceDao
import app.core.data.settings.SettingsStore
import app.core.data.settings.SettingsStores
import app.core.network.DefaultServiceProbe
import app.core.network.ServiceProbe
import app.core.security.CredentialStore
import app.core.tailscale.OkHttpTailscaleControlApiClient
import app.core.tailscale.OkHttpTailscaleLocalApiClient
import app.core.tailscale.TailnetRepository
import app.core.tailscale.TailscaleControlApiClient
import app.core.tailscale.TailscaleLocalApiClient
import app.core.tunnel.LocalProxyServer
import app.core.tunnel.ProxyConfigurator
import app.core.tunnel.TunnelManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    // 设置读写入口：:core:data 里的 DataStore 实现（SettingsStores.create）
    @Provides
    @Singleton
    fun provideSettingsStore(@ApplicationContext context: Context): SettingsStore =
        SettingsStores.create(context)

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

    @Provides
    @Singleton
    fun provideTailnetRepository(
        localApi: TailscaleLocalApiClient,
        controlApi: TailscaleControlApiClient,
        credentials: CredentialStore,
    ): TailnetRepository = TailnetRepository(localApi, controlApi, credentials)

    @Provides
    @Singleton
    fun provideTunnelScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Provides
    @Singleton
    fun provideTunnelManager(
        @ApplicationContext context: Context,
        tailnetRepository: TailnetRepository,
        proxyConfigurator: ProxyConfigurator,
        localProxyServer: LocalProxyServer,
        scope: CoroutineScope,
    ): TunnelManager = TunnelManager(
        context = context,
        tailnetRepository = tailnetRepository,
        proxyConfigurator = proxyConfigurator,
        localProxyServer = localProxyServer,
        scope = scope,
    )

    @Provides
    @Singleton
    fun provideLocalProxyServer(): LocalProxyServer = LocalProxyServer()
}
