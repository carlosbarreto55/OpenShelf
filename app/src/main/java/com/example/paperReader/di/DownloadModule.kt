package com.example.paperReader.di

import android.content.Context
import androidx.work.WorkManager
import com.example.paperReader.data.download.ContentUriProvider
import com.example.paperReader.data.download.DownloadFileStorage
import com.example.paperReader.data.download.FileProviderContentUriProvider
import com.example.paperReader.data.download.OkHttpPdfFetcher
import com.example.paperReader.data.download.PdfHttpFetcher
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadsDir

@Module
@InstallIn(SingletonComponent::class)
object DownloadModule {
    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()

    /** The single directory `res/xml/file_paths.xml` exposes through the app's `FileProvider`. */
    @Provides
    @Singleton
    @DownloadsDir
    fun provideDownloadsDir(@ApplicationContext context: Context): File = File(context.filesDir, "downloads")

    @Provides
    @Singleton
    fun provideDownloadFileStorage(@DownloadsDir downloadsDir: File): DownloadFileStorage = DownloadFileStorage(downloadsDir)

}

@Module
@InstallIn(SingletonComponent::class)
abstract class DownloadBindsModule {
    @Binds
    @Singleton
    abstract fun bindPdfHttpFetcher(impl: OkHttpPdfFetcher): PdfHttpFetcher

    @Binds
    @Singleton
    abstract fun bindContentUriProvider(impl: FileProviderContentUriProvider): ContentUriProvider
}
