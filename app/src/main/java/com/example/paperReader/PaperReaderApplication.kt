package com.example.paperReader

import android.app.Application
import androidx.work.Configuration
import com.example.paperReader.core.config.BackendConfiguration
import com.example.paperReader.core.config.BackendConfigurationResult
import com.example.paperReader.data.download.PaperDownloadWorkerFactory
import com.example.paperReader.data.download.WorkManagerDownloadGateway
import com.example.paperReader.di.ApplicationScope
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class PaperReaderApplication : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(PaperDownloadWorkerFactory())
            .build()

    override fun onCreate() {
        super.onCreate()
        // The WorkManager-backed download path belongs to the integration flavor (see
        // BackendConfiguration); only reconcile it when it is actually selected, so demo launches
        // never touch WorkManager.getInstance() at all. A misconfigured integration build is not
        // failed here — Hilt's BackendConfiguration provider is the fail-fast point, and it
        // reports the problem where the user can see it instead of as an opaque startup crash.
        val configuration = BackendConfiguration.resultFromBuildConfig()
        if (configuration is BackendConfigurationResult.Valid && configuration.configuration.usesHttp) {
            val entryPoint = EntryPointAccessors.fromApplication(this, ReconciliationEntryPoint::class.java)
            entryPoint.applicationScope().launch {
                entryPoint.workManagerDownloadGateway().reconcileInterruptedDownloads()
            }
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReconciliationEntryPoint {
        fun workManagerDownloadGateway(): WorkManagerDownloadGateway

        @ApplicationScope
        fun applicationScope(): CoroutineScope
    }
}
