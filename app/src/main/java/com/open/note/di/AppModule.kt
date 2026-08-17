package com.open.note.di

import android.content.Context
import androidx.room.Room
import com.open.note.data.local.AppDatabase
import com.open.note.data.local.AuthStore
import com.open.note.data.local.dao.FolderDao
import com.open.note.data.local.dao.NoteDao
import com.open.note.data.local.dao.AttachmentDao
import com.open.note.data.remote.api.AuthApi
import com.open.note.data.remote.api.AttachmentApi
import com.open.note.data.remote.api.NoteApi
import com.open.note.data.remote.api.NotebookApi
import com.open.note.data.remote.api.ShareSettingsApi
import com.open.note.data.remote.interceptor.BaseUrlInterceptor
import com.open.note.data.remote.interceptor.JwtInterceptor
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "open_note.db")
            .addMigrations(MIGRATION_3_4)
            .build()

    /**
     * v3 → v4：is_pending_sync 列迁移为 state 四态。
     * 待同步或未上传 → 2 (MODIFIED)，其余 → 1 (SYNCED)
     */
    private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE notes RENAME TO notes_old")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS notes (
                    local_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    server_id TEXT,
                    title TEXT NOT NULL,
                    content TEXT NOT NULL,
                    notebook_id TEXT,
                    color TEXT NOT NULL,
                    is_pinned INTEGER NOT NULL,
                    version INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    deleted_at INTEGER,
                    state INTEGER NOT NULL,
                    last_server_update TEXT
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_server_id ON notes(server_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_notebook_id ON notes(notebook_id)")
            db.execSQL("""
                INSERT INTO notes (local_id, server_id, title, content, notebook_id, color, is_pinned,
                    version, created_at, updated_at, deleted_at, state, last_server_update)
                SELECT local_id, server_id, title, content, notebook_id, color, is_pinned,
                    version, created_at, updated_at, deleted_at,
                    CASE WHEN is_pending_sync = 1 OR server_id IS NULL THEN 2 ELSE 1 END,
                    last_server_update
                FROM notes_old
            """.trimIndent())
            db.execSQL("DROP TABLE notes_old")
        }
    }

    @Provides
    fun provideNoteDao(db: AppDatabase): NoteDao = db.noteDao()

    @Provides
    fun provideFolderDao(db: AppDatabase): FolderDao = db.folderDao()

    @Provides
    fun provideAttachmentDao(db: AppDatabase): AttachmentDao = db.attachmentDao()

    @Provides
    fun provideNoteHistoryDao(db: AppDatabase): com.open.note.data.local.dao.NoteHistoryDao = db.noteHistoryDao()

    @Provides
    @Singleton
    fun provideAuthStore(@ApplicationContext context: Context): AuthStore = AuthStore(context)

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    @Provides
    @Singleton
    fun provideOkHttpClient(
        jwtInterceptor: JwtInterceptor,
        baseUrlInterceptor: BaseUrlInterceptor
    ): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(baseUrlInterceptor)
            .addInterceptor(jwtInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl("http://placeholder/api/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideNoteApi(retrofit: Retrofit): NoteApi = retrofit.create(NoteApi::class.java)

    @Provides
    @Singleton
    fun provideNotebookApi(retrofit: Retrofit): NotebookApi = retrofit.create(NotebookApi::class.java)

    @Provides
    @Singleton
    fun provideShareSettingsApi(retrofit: Retrofit): ShareSettingsApi = retrofit.create(ShareSettingsApi::class.java)

    @Provides
    @Singleton
    fun provideAttachmentApi(retrofit: Retrofit): AttachmentApi = retrofit.create(AttachmentApi::class.java)
}
