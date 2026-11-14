package com.jpage4500.reddittv.util

import io.ktor.utils.io.*
import kotlinx.cinterop.*
import platform.zlib.*

// 15 is zlib's largest window; adding 16 has it expect a gzip header rather than a zlib one
private const val GZIP_WINDOW_BITS = 15 + 16

@OptIn(ExperimentalForeignApi::class)
actual suspend fun ByteReadChannel.forEachInflated(onBytes: (ByteArray, Int) -> Unit) {
    val input = ByteArray(64 * 1024)
    var read = readAvailable(input, 0, input.size)
    if (read <= 0) return
    val isGzipped = read >= 2 && (input[0].toInt() and 0xFF) == GZIP_MAGIC_1 && (input[1].toInt() and 0xFF) == GZIP_MAGIC_2
    if (!isGzipped) {
        while (read > 0) {
            onBytes(input, read)
            if (isClosedForRead) break
            read = readAvailable(input, 0, input.size)
        }
        return
    }
    val output = ByteArray(256 * 1024)
    memScoped {
        val stream = alloc<z_stream>()
        stream.zalloc = null
        stream.zfree = null
        stream.opaque = null
        stream.next_in = null
        stream.avail_in = 0u
        if (inflateInit2_(stream.ptr, GZIP_WINDOW_BITS, ZLIB_VERSION, sizeOf<z_stream>().toInt()) != Z_OK) {
            throw IllegalStateException("zlib wouldn't start")
        }
        try {
            while (read > 0) {
                input.usePinned { pinnedInput ->
                    output.usePinned { pinnedOutput ->
                        stream.next_in = pinnedInput.addressOf(0).reinterpret()
                        stream.avail_in = read.toUInt()
                        while (stream.avail_in > 0u) {
                            stream.next_out = pinnedOutput.addressOf(0).reinterpret()
                            stream.avail_out = output.size.toUInt()
                            val result = inflate(stream.ptr, Z_NO_FLUSH)
                            val produced = output.size - stream.avail_out.toInt()
                            if (produced > 0) onBytes(output, produced)
                            when (result) {
                                Z_OK, Z_BUF_ERROR -> {}
                                // one file can be several gzip streams end to end: on to the next
                                Z_STREAM_END -> if (stream.avail_in > 0u) inflateReset(stream.ptr)
                                else -> throw IllegalStateException("not a gzip file (zlib $result)")
                            }
                            // nothing in, nothing out: more input is needed
                            if (result == Z_BUF_ERROR && produced == 0) break
                        }
                    }
                }
                if (isClosedForRead) break
                read = readAvailable(input, 0, input.size)
            }
        } finally {
            inflateEnd(stream.ptr)
        }
    }
}
