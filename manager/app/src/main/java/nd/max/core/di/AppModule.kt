/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import nd.max.core.threading.DefaultDispatcherProvider
import nd.max.core.threading.DispatcherProvider
import javax.inject.Singleton

/**
 * Application-wide infrastructure bindings.
 *
 * NOTE: IPredictorRepository used to be provided here (backed by a
 * placeholder repository with hardcoded samples) and in DataModule at
 * the same time; that duplicate binding broke Hilt's SingletonComponent.
 * The whole predictor-repository layer was later deleted along with the
 * separate prediction screens — the unified Max AI engine consumes the
 * native predictor directly through PredictorBridge.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDispatcherProvider(): DispatcherProvider = DefaultDispatcherProvider()
}
