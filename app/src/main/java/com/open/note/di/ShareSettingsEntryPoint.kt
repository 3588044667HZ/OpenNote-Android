package com.open.note.di

import com.open.note.data.remote.api.ShareSettingsApi
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ShareSettingsEntryPoint {
    fun shareSettingsApi(): ShareSettingsApi
}
