package com.example.paperReader.data.reader

import com.example.paperReader.data.download.PdfValidation
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * The structural checks that run before the platform renderer ever sees a file.
 *
 * Encryption is deliberately not judged here. An `/Encrypt` dictionary is also how a publisher
 * restricts printing and copying of a PDF that opens without a password, which is common for
 * papers; refusing those would refuse documents the renderer reads fine. Only the renderer knows
 * whether a password is actually needed, so that verdict is left to it.
 */
internal object PdfFileClassifier {
    enum class Verdict {
        /** Does not begin with the PDF signature. */
        NOT_PDF,

        /** Begins like a PDF but its end-of-file marker is missing — a transfer cut short. */
        MALFORMED,

        /** Passes every structural check; the renderer has the last word. */
        CANDIDATE,
    }

    /** Reads only the first and last few bytes, with positional reads that leave the channel's own
     * position alone, so the renderer is handed the descriptor exactly as it was. */
    fun classify(channel: FileChannel): Verdict {
        val size = channel.size()
        val head = channel.readAt(position = 0, length = SIGNATURE_LENGTH)
        if (!PdfValidation.hasPdfSignature(head)) return Verdict.NOT_PDF

        // The marker may be followed by a little trailing whitespace or junk, which readers
        // tolerate, so it is looked for near the end rather than at it.
        val tailLength = minOf(size, TAIL_WINDOW_BYTES.toLong()).toInt()
        val tail = channel.readAt(position = size - tailLength, length = tailLength)
        return if (tail.contains(END_OF_FILE_MARKER)) Verdict.CANDIDATE else Verdict.MALFORMED
    }

    private fun FileChannel.readAt(position: Long, length: Int): ByteArray {
        val buffer = ByteBuffer.allocate(length)
        var offset = position
        while (buffer.hasRemaining()) {
            val read = read(buffer, offset)
            if (read <= 0) break
            offset += read
        }
        return buffer.array().copyOf(buffer.position())
    }

    private fun ByteArray.contains(needle: ByteArray): Boolean {
        if (needle.size > size) return false
        return (0..size - needle.size).any { start -> needle.indices.all { this[start + it] == needle[it] } }
    }

    private const val SIGNATURE_LENGTH = 5
    private const val TAIL_WINDOW_BYTES = 1024
    private val END_OF_FILE_MARKER = "%%EOF".toByteArray(Charsets.US_ASCII)
}
