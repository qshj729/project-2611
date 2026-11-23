package app.di

import android.content.Context
import app.core.data.config.InternalServicesStore
import app.core.data.config.InternalServicesStores
import app.core.data.security.KeystoreSecretStore
import app.core.data.settings.SettingsStore
import app.core.data.settings.SettingsStores
import app.core.model.SecretStore
import app.core.tunnel.LocalProxyServer
import app.core.tunnel.TunnelManager
import app.core.tunnel.TunnelStateProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideLocalProxyServer(): LocalProxyServer = LocalProxyServer()

    @Provides
    @Singleton
    fun provideSettingsStore(@ApplicationContext context: Context): SettingsStore =
        SettingsStores.create(context)

    @Provides
    @Singleton
    fun provideInternalServicesStore(@ApplicationContext context: Context): InternalServicesStore =
        InternalServicesStores.create(context)

    @Provides
    @Singleton
    fun provideSecretStore(@ApplicationContext context: Context): SecretStore =
        KeystoreSecretStore(context)

    @Provides
    @Singleton
    fun provideTunnelScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Provides
    @Singleton
    fun provideTunnelManager(
        @ApplicationContext context: Context,
        secretStore: SecretStore,
        localProxyServer: LocalProxyServer,
        scope: CoroutineScope,
    ): TunnelManager = TunnelManager(
        context = context,
        secretStore = secretStore,
        localProxyServer = localProxyServer,
        scope = scope,
    )

    @Provides
    @Singleton
    fun provideTunnelStateProvider(tunnelManager: TunnelManager): TunnelStateProvider = tunnelManager
}
