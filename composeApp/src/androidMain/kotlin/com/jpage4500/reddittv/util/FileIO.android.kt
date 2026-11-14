package com.jpage4500.reddittv.util

import java.io.File

actual fun createFileIO(): FileIO {
    return AndroidFileIO()
}

/**
 * android file I/O implementation
 * stores files relative to app internal storage root
 */
class AndroidFileIO : FileIO {
    private val log = LoggerConfig.createLogger("AndroidFileIO")

    init {
        log.d { "init: base directory: ${getBaseDir().absolutePath}" }
    }

    private fun getBaseDir(): File {
        return AndroidContextProvider.context.filesDir
    }

    override fun writeFile(folderPath: String, fileName: String, content: String, append: Boolean): Boolean {
        return try {
            val folder = File(getBaseDir(), folderPath)
            folder.mkdirs()
            val file = File(folder, fileName)
            if (append) {
                file.appendText(content)
            } else {
                file.writeText(content)
            }
            true
        } catch (e: Exception) {
            // print directly: routing through log.e would re-enter StorageManager.writeFile
            // and try to persist this error to the same file that just failed
            println("AndroidFileIO: writeFile: exception for $folderPath/$fileName: ${e.message}")
            false
        }
    }

    override fun writeBytes(folderPath: String, fileName: String, bytes: ByteArray): Boolean {
        return try {
            val folder = File(getBaseDir(), folderPath)
            folder.mkdirs()
            File(folder, fileName).writeBytes(bytes)
            true
        } catch (e: Exception) {
            log.e { "writeBytes: exception for $folderPath/$fileName: ${e.message}" }
            false
        }
    }

    override fun readFile(folderPath: String, fileName: String): String? {
        return try {
            val folder = File(getBaseDir(), folderPath)
            val file = File(folder, fileName)
            if (!file.exists()) {
                return null
            }
            file.readText()
        } catch (e: Exception) {
            log.e { "readFile: exception for $folderPath/$fileName: ${e.message}" }
            null
        }
    }

    override fun getAbsolutePath(folderPath: String, fileName: String): String? {
        val file = File(File(getBaseDir(), folderPath), fileName)
        return if (file.exists()) file.absolutePath else null
    }

    override fun getFileDetails(folderPath: String, fileName: String): FileMetadata? {
        return try {
            val folder = File(getBaseDir(), folderPath)
            val file = File(folder, fileName)
            if (!file.exists()) {
                return null
            }
            FileMetadata(fileName, file.lastModified(), file.length())
        } catch (e: Exception) {
            log.e { "getFileDetails: exception for $folderPath/$fileName: ${e.message}" }
            null
        }
    }

    override fun listFiles(folderPath: String, extension: String?): List<String> {
        return try {
            val folder = File(getBaseDir(), folderPath)
            if (!folder.exists()) {
                return emptyList()
            }

            val files = folder.listFiles { file ->
                file.isFile && (extension == null || file.extension == extension)
            } ?: emptyArray()
            files.map { it.name }
        } catch (e: Exception) {
            log.e { "listFiles: exception for $folderPath: ${e.message}" }
            emptyList()
        }
    }

    override fun deleteFile(folderPath: String, fileName: String): Boolean {
        return try {
            val folder = File(getBaseDir(), folderPath)
            val file = File(folder, fileName)
            file.delete()
        } catch (e: Exception) {
            log.e { "deleteFile: exception for $folderPath/$fileName: ${e.message}" }
            false
        }
    }

    override fun deleteAllFiles(folderPath: String, extension: String?): Boolean {
        return try {
            val folder = File(getBaseDir(), folderPath)
            if (!folder.exists()) {
                return true
            }

            val files = folder.listFiles { file ->
                file.isFile && (extension == null || file.extension == extension)
            } ?: emptyArray()

            files.forEach { it.delete() }
            true
        } catch (e: Exception) {
            log.e { "deleteAllFiles: exception for $folderPath: ${e.message}" }
            false
        }
    }

    override fun listFileMetadata(folderPath: String, extension: String?): List<FileMetadata> {
        return try {
            val folder = File(getBaseDir(), folderPath)
            if (!folder.exists()) {
                return emptyList()
            }
            val files = folder.listFiles { file ->
                file.isFile && (extension == null || file.extension == extension)
            } ?: emptyArray()
            files.map { file ->
                FileMetadata(
                    name = file.name,
                    lastModified = file.lastModified(),
                    size = file.length()
                )
            }
        } catch (e: Exception) {
            log.e { "listFileMetadata: exception for $folderPath: ${e.message}" }
            emptyList()
        }
    }

    override fun renameFile(folderPath: String, oldName: String, newName: String): Boolean {
        return try {
            val folder = File(getBaseDir(), folderPath)
            val oldFile = File(folder, oldName)
            val newFile = File(folder, newName)
            oldFile.renameTo(newFile)
        } catch (e: Exception) {
            log.e { "renameFile: exception for $folderPath/$oldName->$newName: ${e.message}" }
            false
        }
    }
}

