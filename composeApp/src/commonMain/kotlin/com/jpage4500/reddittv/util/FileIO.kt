package com.jpage4500.reddittv.util

import com.jpage4500.reddittv.util.FileMetadata

/**
 * expect/actual for platform-specific file I/O implementation
 */
expect fun createFileIO(): FileIO

/**
 * platform-specific file I/O operations interface
 * platform implementations only need to handle basic file operations
 * this interface is generic and can be used for any file storage needs
 */
interface FileIO {
    /**
     * write a string to a file
     * @param folderPath the folder path (relative to app storage root)
     * @param fileName the file name (without path)
     * @param content the string content to write
     * @param append whether to append to the file (default false)
     * @return true if successful
     */
    fun writeFile(folderPath: String, fileName: String, content: String, append: Boolean): Boolean

    /**
     * write raw bytes to a file, replacing whatever was there.
     *
     * everything else here is text — this exists for the imported background photos
     * (see [com.jpage4500.reddittv.manager.PersonalPhotoManager]), which are the only binary the app
     * writes itself.
     *
     * @param folderPath the folder path (relative to app storage root)
     * @param fileName the file name (without path)
     * @param bytes the content to write
     * @return true if successful
     */
    fun writeBytes(folderPath: String, fileName: String, bytes: ByteArray): Boolean

    /**
     * read a string from a file
     * @param folderPath the folder path (relative to app storage root)
     * @param fileName the file name (without path)
     * @return the file content or null if file doesn't exist or read failed
     */
    fun readFile(folderPath: String, fileName: String): String?

    /**
     * get the absolute filesystem path for a file, or null if it doesn't exist.
     * used to hand a file off to the platform's default viewer (see PlatformUtils.openUrl)
     */
    fun getAbsolutePath(folderPath: String, fileName: String): String?

    /**
     * get file details
     */
    fun getFileDetails(folderPath: String, fileName: String): FileMetadata?

    /**
     * list all file names in the specified folder
     * @param folderPath the folder path (relative to app storage root)
     * @param extension optional file extension filter (e.g., "json"), null for all files
     * @return list of file names (without path)
     */
    fun listFiles(folderPath: String, extension: String? = null): List<String>

    /**
     * list all file metadata in the specified folder
     * @param folderPath the folder path (relative to app storage root)
     * @param extension optional file extension filter (e.g., "json"), null for all files
     * @return list of FileMetadata
     */
    fun listFileMetadata(folderPath: String, extension: String? = null): List<FileMetadata>

    /**
     * delete a file
     * @param folderPath the folder path (relative to app storage root)
     * @param fileName the file name (without path)
     * @return true if successful
     */
    fun deleteFile(folderPath: String, fileName: String): Boolean

    /**
     * delete all files in the specified folder
     * @param folderPath the folder path (relative to app storage root)
     * @param extension optional file extension filter (e.g., "json"), null for all files
     * @return true if successful
     */
    fun deleteAllFiles(folderPath: String, extension: String? = null): Boolean

    /**
     * rename a file in the specified folder
     * @return true if successful
     */
    fun renameFile(folderPath: String, oldName: String, newName: String): Boolean
}

