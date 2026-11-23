package app.di

import app.core.data.config.InternalServicesStore
import app.core.tunnel.LocalProxyServer
import app.core.tunnel.TunnelManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Entry point for accessing bindings from [MyAppsApplication] before Hilt
 * has injected into other components.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface MyAppsApplicationEntryPoint {
    fun localProxyServer(): LocalProxyServer
    fun internalServicesStore(): InternalServicesStore
    fun tunnelManager(): TunnelManager
}
