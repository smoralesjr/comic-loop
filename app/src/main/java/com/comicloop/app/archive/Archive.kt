package com.comicloop.app.archive

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

data class ArchivePage(
    val index: Int,
    val fileName: String,
    val open: () -> InputStream
)

interface ArchiveReader {
    val pageCount: Int
    fun pageAt(index: Int): ArchivePage
    fun close()
}

object ImageTypes {
    private val EXTS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "avif")

    fun isImage(name: String): Boolean {
        if (name.contains("__MACOSX") || name.contains("/._")) return false
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in EXTS
    }
}

class CbzReader(private val zipFile: File) : ArchiveReader {
    private val entries: List<String> = net.lingala.zip4j.ZipFile(zipFile)
        .fileHeaders
        .map { it.fileName }
        .filter { ImageTypes.isImage(it) }
        .sortedWith(String.CASE_INSENSITIVE_ORDER)

    override val pageCount: Int get() = entries.size

    override fun pageAt(index: Int): ArchivePage {
        val name = entries[index]
        return ArchivePage(index, name.substringAfterLast('/')) {
            val zip = net.lingala.zip4j.ZipFile(zipFile)
            val header = zip.getFileHeader(name) ?: error("Missing entry $name")
            val bytes = ByteArrayOutputStream().also { out ->
                zip.getInputStream(header).use { it.copyTo(out) }
            }.toByteArray()
            zip.close()
            bytes.inputStream()
        }
    }

    override fun close() {}
}

class CbrReader(rarFile: File) : ArchiveReader {
    private val file = rarFile
    private val names: List<String> = com.github.junrar.Archive(file).use { archive ->
        archive.fileHeaders
            .filter { !it.isDirectory && ImageTypes.isImage(it.fileNameString) }
            .map { it.fileNameString }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    override val pageCount: Int get() = names.size

    override fun pageAt(index: Int): ArchivePage {
        val name = names[index]
        return ArchivePage(index, name.substringAfterLast('/')) {
            val archive = com.github.junrar.Archive(file)
            val header = archive.fileHeaders.first { it.fileNameString == name }
            val bytes = ByteArrayOutputStream().also { archive.extractFile(header, it) }.toByteArray()
            archive.close()
            bytes.inputStream()
        }
    }

    override fun close() {}
}

object ArchiveFactory {
    fun detect(fileName: String): String? {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".cbz") || lower.endsWith(".zip") -> "cbz"
            lower.endsWith(".cbr") || lower.endsWith(".rar") -> "cbr"
            else -> null
        }
    }

    fun open(file: File, kind: String): ArchiveReader = when (kind) {
        "cbz" -> CbzReader(file)
        "cbr" -> CbrReader(file)
        else -> throw IllegalArgumentException("Unsupported archive: $kind")
    }

    fun copyToCache(context: Context, uri: Uri, displayName: String): File {
        val cacheDir = File(context.cacheDir, "archives").apply { mkdirs() }
        val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
        val target = File(cacheDir, "${uri.hashCode().toUInt()} $safeName")
        if (target.exists() && target.length() > 0L) return target
        context.contentResolver.openInputStream(uri)!!.use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
        return target
    }
}
