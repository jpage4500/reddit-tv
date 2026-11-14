package com.jpage4500.reddittv.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * file metadata for cross-platform file info
 */
data class FileMetadata(val name: String, val lastModified: Long, val size: Long)

/**
 * singleton storage manager for the app
 * used for logs, files, backups, etc
 */
object StorageManager {
    private val log = LoggerConfig.createLogger("StorageManager")
    private val storage: FileIO by lazy { createFileIO() }

    // single-threaded background dispatcher for file writes.
    // must be serialized: Windows holds an exclusive lock on append/rename, so two concurrent
    // writes to the same file fail with "process cannot access the file because it is being
    // used by another process". Dispatchers.Default is a thread pool, not single-threaded.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val writeDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val writeScope = CoroutineScope(writeDispatcher + SupervisorJob())

    /**
     * write a string to a file in a single background thread
     * @param append whether to append to the file (default false)
     */
    fun writeFile(folder: String, fileName: String, content: String, append: Boolean = false, onComplete: ((Boolean) -> Unit)? = null) {
        writeScope.launch {
            val isOk = storage.writeFile(folder, fileName, content, append)
            onComplete?.invoke(isOk)
        }
    }

    // synchronous write — used by the crash handler from the uncaught-exception handler,
    // where the process is about to die and the background writeScope can't be trusted to flush.
    fun writeFileSync(folder: String, fileName: String, content: String, append: Boolean = false): Boolean {
        return storage.writeFile(folder, fileName, content, append)
    }

    /**
     * write raw bytes, on the same serialized thread the text writes use — suspending rather than
     * fire-and-forget because the caller (a photo import) needs to know whether the copy landed
     * before it counts it.
     */
    suspend fun writeBytes(folder: String, fileName: String, bytes: ByteArray): Boolean {
        return withContext(writeDispatcher) { storage.writeBytes(folder, fileName, bytes) }
    }

    fun readFile(folder: String, fileName: String): String? {
        return storage.readFile(folder, fileName)
    }

    /**
     * absolute filesystem path for a file, or null if it doesn't exist
     */
    fun getAbsolutePath(folder: String, fileName: String): String? {
        return storage.getAbsolutePath(folder, fileName)
    }

    fun getFileDetails(folder: String, fileName: String): FileMetadata? {
        return storage.listFileMetadata(folder).firstOrNull { it.name == fileName }
    }

    fun listFiles(folder: String, extension: String? = null): List<String> {
        return storage.listFiles(folder, extension)
    }

    /**
     * list all file metadata in the specified folder
     */
    fun listFileMetadata(folder: String): List<FileMetadata> {
        return storage.listFileMetadata(folder)
    }

    fun deleteFile(folder: String, fileName: String): Boolean {
        return storage.deleteFile(folder, fileName)
    }

    fun deleteAllFiles(folder: String, fileName: String): Boolean {
        return storage.deleteAllFiles(folder, fileName)
    }

    fun renameFile(folder: String, oldName: String, newName: String): Boolean {
        return storage.renameFile(folder, oldName, newName)
    }
}
