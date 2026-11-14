package com.jpage4500.reddittv.util

import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.*
import java.io.*
import java.util.zip.*

actual suspend fun ByteReadChannel.forEachInflated(onBytes: (ByteArray, Int) -> Unit) = withContext(Dispatchers.IO) {
    val input = BufferedInputStream(toInputStream(), 64 * 1024)
    // a look at the first two bytes, then back to the start
    input.mark(2)
    val isGzipped = input.read() == GZIP_MAGIC_1 && input.read() == GZIP_MAGIC_2
    input.reset()
    (if (isGzipped) GZIPInputStream(input, 64 * 1024) else input).use { stream ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            if (read > 0) onBytes(buffer, read)
        }
    }
}
