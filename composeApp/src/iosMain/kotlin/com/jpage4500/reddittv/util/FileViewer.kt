package com.jpage4500.reddittv.util

interface FileViewer {
    /** present the file at [path] in a native previewer (QLPreviewController) */
    fun viewFile(path: String)
}

var globalFileViewer: FileViewer? = null
