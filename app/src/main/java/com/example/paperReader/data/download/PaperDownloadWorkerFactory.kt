package com.example.paperReader.data.download

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.example.paperReader.core.Clock
import com.example.paperReader.data.local.dao.DownloadDao
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DownloadWorkerDependenciesEntryPoint {
    fun downloadDao(): DownloadDao

    fun pdfDownloader(): PdfDownloader

    fun contentUriProvider(): ContentUriProvider

    fun clock(): Clock
}

/** WorkManager instantiates workers itself, outside the normal Hilt injection sites, so
 * [PdfDownloadWorker]'s dependencies are pulled from the singleton component through this factory
 * instead of pulling in the `androidx.hilt:hilt-work` artifact for one inactive worker. Returning
 * `null` for any other worker class defers to WorkManager's default reflection-based factory. */
class PaperDownloadWorkerFactory : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? {
        if (workerClassName != PdfDownloadWorker::class.java.name) return null

        val entryPoint = EntryPoints.get(
            appContext.applicationContext,
            DownloadWorkerDependenciesEntryPoint::class.java,
        )
        return PdfDownloadWorker(
            context = appContext,
            params = workerParameters,
            downloadDao = entryPoint.downloadDao(),
            downloader = entryPoint.pdfDownloader(),
            contentUriProvider = entryPoint.contentUriProvider(),
            clock = entryPoint.clock(),
        )
    }
}
