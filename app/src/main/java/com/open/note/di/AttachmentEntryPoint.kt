package com.open.note.di

import com.open.note.data.local.dao.AttachmentDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AttachmentEntryPoint {
    fun attachmentDao(): AttachmentDao
}
