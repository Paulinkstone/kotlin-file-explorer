package com.esgi4al.paulin.kfileexplorer.models

import com.esgi4al.paulin.kfileexplorer.common.FileType

data class FileModel(
        val path: String,
        val fileType: FileType,
        val name: String,
        val sizeInMB: Double,
        val extension: String = "",
        val subFiles: Int = 0
)