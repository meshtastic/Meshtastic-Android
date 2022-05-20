package com.geeksville.mesh.repository.radio

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object RadioRepositoryModule {
    @Provides
    @RadioRepositoryQualifier
    fun provideSharedPreferences(application: Application): SharedPreferences {
        return application.getSharedPreferences("radio-prefs", Context.MODE_PRIVATE)
    }
}