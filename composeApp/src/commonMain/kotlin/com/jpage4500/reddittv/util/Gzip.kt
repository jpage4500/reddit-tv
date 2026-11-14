package com.jpage4500.reddittv.util

import io.ktor.utils.io.*

/**
 * the body's bytes, a piece at a time — unzipped first when it's a gzip file (a guide is often
 * served as `.xml.gz`), which its first two bytes say. common code has no inflater, so each
 * platform brings its own: java.util.zip on Android and the desktop, zlib on iOS.
 *
 * not for a response that's only compressed in transit (Content-Encoding): the HTTP client has
 * already undone that
 * @param onBytes the array is reused: only its first [Int] bytes are this piece
 */
expect suspend fun ByteReadChannel.forEachInflated(onBytes: (ByteArray, Int) -> Unit)

// every gzip file starts with these
internal const val GZIP_MAGIC_1 = 0x1f
internal const val GZIP_MAGIC_2 = 0x8b
