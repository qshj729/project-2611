package com.hubdeck.app.di

import com.hubdeck.app.ProxyConfigurator
import com.hubdeck.app.core.tailscale.TailnetRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Entry point for accessing bindings from [HubDeckApplication] before Hilt
 * has injected into other components.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface HubDeckApplicationEntryPoint {
    fun proxyConfigurator(): ProxyConfigurator
    fun tailnetRepository(): TailnetRepository
}