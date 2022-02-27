package com.geeksville.mesh

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@InstallIn(SingletonComponent::class)
@Module
object ApplicationModule {
    @Provides
    fun provideSharedPreferences(application: Application): SharedPreferences {
        return application.getSharedPreferences("ui-prefs", Context.MODE_PRIVATE)
    }

    @Provides
    fun provideProcessLifecycleOwner(): LifecycleOwner {
        return ProcessLifecycleOwner.get()
    }

    @Provides
    fun provideProcessLifecycle(processLifecycleOwner: LifecycleOwner): Lifecycle {
        return processLifecycleOwner.lifecycle
    }
}