package com.jpage4500.reddittv.util

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.*

actual fun createFileIO(): FileIO {
    return IosFileIO()
}

/**
 * iOS file I/O implementation
 * stores files relative to Documents directory
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosFileIO : FileIO {
    private val log = LoggerConfig.createLogger("IosFileIO")
    private val fileManager = NSFileManager.defaultManager
    private val baseDir: String

    init {
        // get documents directory
        baseDir = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true
        ).first() as String

        log.d { "init: base directory: $baseDir" }
    }

    override fun writeFile(folderPath: String, fileName: String, content: String, append: Boolean): Boolean {
        try {
            val folderFullPath = "$baseDir/$folderPath"
            val folderURL = NSURL.fileURLWithPath(folderFullPath)
            if (!fileManager.fileExistsAtPath(folderFullPath)) {
                fileManager.createDirectoryAtURL(
                    folderURL,
                    true,
                    null,
                    null
                )
            }
            val filePath = "$folderFullPath/$fileName"
            if (append && fileManager.fileExistsAtPath(filePath)) {
                val handle = NSFileHandle.fileHandleForWritingAtPath(filePath)
                if (handle != null) {
                    handle.seekToEndOfFile()
                    val data = NSString.create(string = content).dataUsingEncoding(NSUTF8StringEncoding)
                    if (data != null) {
                        handle.writeData(data)
                    }
                    handle.closeFile()
                    return true
                } else {
                    // print directly: routing through log.e would re-enter StorageManager.writeFile
                    println("IosFileIO: writeFile: failed to open file handle for $filePath")
                    return false
                }
            } else {
                NSString.create(string = content).writeToFile(
                    filePath,
                    true,
                    NSUTF8StringEncoding,
                    null
                )
                return true
            }
        } catch (e: Exception) {
            // print directly: routing through log.e would re-enter StorageManager.writeFile
            // and try to persist this error to the same file that just failed
            println("IosFileIO: writeFile: exception for $folderPath/$fileName: ${e.message}")
            return false
        }
    }

    override fun writeBytes(folderPath: String, fileName: String, bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        return try {
            val folderFullPath = "$baseDir/$folderPath"
            if (!fileManager.fileExistsAtPath(folderFullPath)) {
                fileManager.createDirectoryAtURL(
                    NSURL.fileURLWithPath(folderFullPath),
                    true,
                    null,
                    null
                )
            }
            val data = bytes.usePinned { pinned ->
                NSData.dataWithBytes(pinned.addressOf(0), bytes.size.convert())
            }
            data.writeToFile("$folderFullPath/$fileName", atomically = true)
        } catch (e: Exception) {
            log.e { "writeBytes: exception for $folderPath/$fileName: ${e.message}" }
            false
        }
    }

    override fun readFile(folderPath: String, fileName: String): String? {
        return try {
            val filePath = "$baseDir/$folderPath/$fileName"

            if (!fileManager.fileExistsAtPath(filePath)) {
                return null
            }

            NSString.stringWithContentsOfFile(
                filePath,
                NSUTF8StringEncoding,
                null
            ) as String
        } catch (e: Exception) {
            log.e { "readFile: exception for $folderPath/$fileName: ${e.message}" }
            null
        }
    }

    override fun listFiles(folderPath: String, extension: String?): List<String> {
        return try {
            val folderFullPath = "$baseDir/$folderPath"

            if (!fileManager.fileExistsAtPath(folderFullPath)) {
                return emptyList()
            }

            val contents = fileManager.contentsOfDirectoryAtPath(folderFullPath, null)
            val files = contents?.filterIsInstance<String>() ?: emptyList()

            if (extension != null) {
                files.filter { it.endsWith(".$extension") }
            } else {
                files
            }
        } catch (e: Exception) {
            log.e { "listFiles: exception for $folderPath: ${e.message}" }
            emptyList()
        }
    }

    override fun getAbsolutePath(folderPath: String, fileName: String): String? {
        val filePath = "$baseDir/$folderPath/$fileName"
        return if (fileManager.fileExistsAtPath(filePath)) filePath else null
    }

    override fun getFileDetails(folderPath: String, fileName: String): FileMetadata? {
        val filePath = "$baseDir/$folderPath/$fileName"
        if (!fileManager.fileExistsAtPath(filePath)) {
            return null
        }
        val attrs = fileManager.attributesOfItemAtPath(filePath, null)
        val modDate = attrs?.get(NSFileModificationDate) as? NSDate
        val size = attrs?.get(NSFileSize) as? NSNumber
        return FileMetadata(
            name = fileName,
            lastModified = (modDate?.timeIntervalSince1970?.times(1000))?.toLong() ?: 0L,
            size = size?.longValue ?: 0L
        )
    }

    override fun deleteFile(folderPath: String, fileName: String): Boolean {
        return try {
            val filePath = "$baseDir/$folderPath/$fileName"
            fileManager.removeItemAtPath(filePath, null)
            true
        } catch (e: Exception) {
            log.e { "deleteFile: exception for $folderPath/$fileName: ${e.message}" }
            false
        }
    }

    override fun deleteAllFiles(folderPath: String, extension: String?): Boolean {
        return try {
            val folderFullPath = "$baseDir/$folderPath"

            if (!fileManager.fileExistsAtPath(folderFullPath)) {
                return true
            }

            val contents = fileManager.contentsOfDirectoryAtPath(folderFullPath, null) as? List<*>
            val files = contents?.filterIsInstance<String>() ?: emptyList()

            val filesToDelete = if (extension != null) {
                files.filter { it.endsWith(".$extension") }
            } else {
                files
            }

            filesToDelete.forEach { fileName ->
                try {
                    val filePath = "$folderFullPath/$fileName"
                    fileManager.removeItemAtPath(filePath, null)
                } catch (e: Exception) {
                    log.e { "deleteAllFiles: failed to delete $fileName: ${e.message}" }
                }
            }

            true
        } catch (e: Exception) {
            log.e { "deleteAllFiles: exception for $folderPath: ${e.message}" }
            false
        }
    }

    override fun listFileMetadata(folderPath: String, extension: String?): List<FileMetadata> {
        return try {
            val folderFullPath = "$baseDir/$folderPath"
            if (!fileManager.fileExistsAtPath(folderFullPath)) {
                return emptyList()
            }
            val contents = fileManager.contentsOfDirectoryAtPath(folderFullPath, null) as? List<*>
            val files = contents?.filterIsInstance<String>() ?: emptyList()
            val filteredFiles = if (extension != null) {
                files.filter { it.endsWith(".$extension") }
            } else {
                files
            }
            filteredFiles.map { fileName ->
                val filePath = "$folderFullPath/$fileName"
                val attrs = fileManager.attributesOfItemAtPath(filePath, null)
                val modDate = attrs?.get(NSFileModificationDate) as? NSDate
                val size = attrs?.get(NSFileSize) as? NSNumber
                FileMetadata(
                    name = fileName,
                    lastModified = (modDate?.timeIntervalSince1970?.times(1000))?.toLong() ?: 0L,
                    size = size?.longValue ?: 0L
                )
            }
        } catch (e: Exception) {
            log.e { "listFileMetadata: exception for $folderPath: ${e.message}" }
            emptyList()
        }
    }

    override fun renameFile(folderPath: String, oldName: String, newName: String): Boolean {
        return try {
            val oldPath = "$baseDir/$folderPath/$oldName"
            val newPath = "$baseDir/$folderPath/$newName"
            // NOTE: fails (returns false, no exception) if newPath already exists - callers
            // that want to overwrite must delete the destination first
            fileManager.moveItemAtPath(oldPath, newPath, null)
        } catch (e: Exception) {
            log.e { "renameFile: exception for $folderPath/$oldName->$newName: ${e.message}" }
            false
        }
    }
}

