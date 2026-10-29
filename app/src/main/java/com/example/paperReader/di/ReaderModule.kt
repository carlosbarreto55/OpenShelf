package com.example.paperReader.di

import com.example.paperReader.data.reader.AndroidPdfDocumentFactory
import com.example.paperReader.data.reader.ContentResolverFileOpener
import com.example.paperReader.data.reader.ManagedFileOpener
import com.example.paperReader.data.reader.PdfPageSource
import com.example.paperReader.data.reader.PdfRendererPageSource
import com.example.paperReader.data.reader.PlatformPdfDocument
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ReaderModule {
    /** Deliberately unscoped: a page source holds one open document, so every reader screen gets
     * its own and releases it when it goes. */
    @Binds
    abstract fun bindPdfPageSource(impl: PdfRendererPageSource): PdfPageSource

    @Binds
    abstract fun bindManagedFileOpener(impl: ContentResolverFileOpener): ManagedFileOpener

    @Binds
    abstract fun bindPlatformPdfDocumentFactory(impl: AndroidPdfDocumentFactory): PlatformPdfDocument.Factory
}
