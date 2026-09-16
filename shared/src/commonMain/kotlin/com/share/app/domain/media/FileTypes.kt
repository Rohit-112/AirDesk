package com.share.app.domain.media

import com.share.app.domain.policy.SessionLimits

/**
 * What a file actually is.
 *
 * The data channel carries only a file's name, and names are unreliable: a
 * photo called ".jpg" is often HEIC underneath, and plenty of files have no
 * extension at all. The first few bytes are reliable, so they win whenever
 * they are conclusive and the name is only the fallback.
 *
 * A port of the web client's `domain/fileType.ts`; the two have to agree, or
 * the same file shows up as two different things on either end.
 */
enum class FileKind { IMAGE, VIDEO, AUDIO, PDF, DOCUMENT, SPREADSHEET, PRESENTATION, ARCHIVE, CODE, TEXT, OTHER }

data class DetectedFileType(
    val mimeType: String,
    val kind: FileKind,
    /** A short badge label such as "JPG", "PDF" or "HEIC". */
    val label: String,
)

object FileTypes {
    /** Enough for every signature below, including the brands in an ISO media header. */
    const val SNIFF_BYTE_COUNT = 32

    private const val DEFAULT_TYPE = SessionLimits.DEFAULT_CONTENT_TYPE

    private const val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    private const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    private const val PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    private const val ODT = "application/vnd.oasis.opendocument.text"
    private const val ODS = "application/vnd.oasis.opendocument.spreadsheet"
    private const val ODP = "application/vnd.oasis.opendocument.presentation"
    private const val APK = "application/vnd.android.package-archive"

    /** Ordered: the first extension listed for a type is the one its label uses. */
    private val EXTENSION_TYPES: Map<String, String> = linkedMapOf(
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "jfif" to "image/jpeg",
        "png" to "image/png",
        "gif" to "image/gif",
        "webp" to "image/webp",
        "avif" to "image/avif",
        "heic" to "image/heic",
        "heif" to "image/heif",
        "bmp" to "image/bmp",
        "svg" to "image/svg+xml",
        "ico" to "image/x-icon",
        "tif" to "image/tiff",
        "tiff" to "image/tiff",

        "mp4" to "video/mp4",
        "m4v" to "video/mp4",
        "mov" to "video/quicktime",
        "webm" to "video/webm",
        "mkv" to "video/x-matroska",
        "avi" to "video/x-msvideo",
        "3gp" to "video/3gpp",

        "mp3" to "audio/mpeg",
        "m4a" to "audio/mp4",
        "aac" to "audio/aac",
        "wav" to "audio/wav",
        "ogg" to "audio/ogg",
        "opus" to "audio/opus",
        "flac" to "audio/flac",

        "pdf" to "application/pdf",

        "docx" to DOCX,
        "doc" to "application/msword",
        "odt" to ODT,
        "rtf" to "application/rtf",
        "xlsx" to XLSX,
        "xls" to "application/vnd.ms-excel",
        "ods" to ODS,
        "csv" to "text/csv",
        "pptx" to PPTX,
        "ppt" to "application/vnd.ms-powerpoint",
        "odp" to ODP,

        "zip" to "application/zip",
        "rar" to "application/vnd.rar",
        "7z" to "application/x-7z-compressed",
        "gz" to "application/gzip",
        "tar" to "application/x-tar",
        "apk" to APK,

        "txt" to "text/plain",
        "log" to "text/plain",
        "md" to "text/markdown",

        "json" to "application/json",
        "xml" to "application/xml",
        "yaml" to "application/yaml",
        "yml" to "application/yaml",
        "html" to "text/html",
        "htm" to "text/html",
        "css" to "text/css",
        "js" to "text/javascript",
        "mjs" to "text/javascript",
        "jsx" to "text/javascript",
        "ts" to "text/x-typescript",
        "tsx" to "text/x-typescript",
        "py" to "text/x-python",
        "java" to "text/x-java",
        "kt" to "text/x-kotlin",
        "swift" to "text/x-swift",
        "c" to "text/x-c",
        "h" to "text/x-c",
        "cpp" to "text/x-c++",
        "go" to "text/x-go",
        "rs" to "text/x-rust",
        "rb" to "text/x-ruby",
        "php" to "text/x-php",
        "sh" to "application/x-sh",
        "sql" to "application/sql",
    )

    private val PREFERRED_EXTENSION: Map<String, String> = buildMap {
        EXTENSION_TYPES.forEach { (extension, mimeType) -> if (mimeType !in this) put(mimeType, extension) }
    }

    private val DOCUMENT_TYPES = setOf(DOCX, ODT, "application/msword", "application/rtf")
    private val SPREADSHEET_TYPES = setOf(XLSX, ODS, "application/vnd.ms-excel", "text/csv")
    private val PRESENTATION_TYPES = setOf(PPTX, ODP, "application/vnd.ms-powerpoint")
    private val ARCHIVE_TYPES = setOf(
        "application/zip",
        "application/vnd.rar",
        "application/x-7z-compressed",
        "application/gzip",
        "application/x-tar",
        APK,
    )
    private val CODE_TYPES = setOf(
        "application/json",
        "application/xml",
        "application/yaml",
        "text/html",
        "text/css",
        "text/javascript",
        "text/x-typescript",
        "text/x-python",
        "text/x-java",
        "text/x-kotlin",
        "text/x-swift",
        "text/x-c",
        "text/x-c++",
        "text/x-go",
        "text/x-rust",
        "text/x-ruby",
        "text/x-php",
        "application/x-sh",
        "application/sql",
    )

    /** Office files and APKs are zip archives underneath; only the name tells them apart. */
    private val ZIP_CONTAINER_TYPES = setOf(DOCX, XLSX, PPTX, ODT, ODS, ODP, APK)

    /** Still-image brands that can appear in an ISO media header. */
    private val HEIC_BRANDS = setOf("heic", "heix", "hevc", "hevx", "heim", "heis", "hevm", "hevs")

    private fun ByteArray.byteAt(index: Int): Int = if (index in indices) this[index].toInt() and 0xff else 0

    private fun ByteArray.matches(signature: IntArray, offset: Int = 0): Boolean =
        size >= offset + signature.size && signature.indices.all { byteAt(offset + it) == signature[it] }

    private fun ByteArray.ascii(offset: Int, length: Int): String {
        if (size < offset + length) return ""
        return buildString(length) { for (index in offset until offset + length) append(byteAt(index).toChar()) }
    }

    /** MP4, MOV, HEIC and AVIF share one container format; its brands say which it is. */
    private fun sniffIsoMedia(bytes: ByteArray): String? {
        if (bytes.ascii(4, 4) != "ftyp") return null

        val boxSize = (bytes.byteAt(0).toLong() shl 24) or
            (bytes.byteAt(1).toLong() shl 16) or
            (bytes.byteAt(2).toLong() shl 8) or
            bytes.byteAt(3).toLong()
        val end = minOf(bytes.size.toLong(), if (boxSize >= 16) boxSize else bytes.size.toLong()).toInt()
        val brands = mutableListOf(bytes.ascii(8, 4))
        var offset = 16
        while (offset + 4 <= end) {
            brands += bytes.ascii(offset, 4)
            offset += 4
        }

        // A specific still-image brand outranks the generic one an image also lists.
        if (brands.any { it == "avif" || it == "avis" }) return "image/avif"
        if (brands.any { it in HEIC_BRANDS }) return "image/heic"
        if (brands.any { it == "mif1" || it == "msf1" }) return "image/heif"

        val major = brands.first()
        return when {
            major == "qt  " -> "video/quicktime"
            major == "M4A " || major == "M4B " -> "audio/mp4"
            major.startsWith("3g") -> "video/3gpp"
            else -> "video/mp4"
        }
    }

    /** The type the leading bytes prove, or null when they prove nothing. */
    fun sniffMimeType(bytes: ByteArray): String? {
        if (bytes.matches(intArrayOf(0xff, 0xd8, 0xff))) return "image/jpeg"
        if (bytes.matches(intArrayOf(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))) return "image/png"
        val gif = bytes.ascii(0, 6)
        if (gif == "GIF87a" || gif == "GIF89a") return "image/gif"
        if (bytes.ascii(0, 4) == "RIFF") {
            when (bytes.ascii(8, 4)) {
                "WEBP" -> return "image/webp"
                "WAVE" -> return "audio/wav"
                "AVI " -> return "video/x-msvideo"
            }
        }
        // "BM" alone is too common at the start of a text file; the reserved bytes must be zero too.
        if (bytes.ascii(0, 2) == "BM" && bytes.matches(intArrayOf(0, 0, 0, 0), offset = 6)) return "image/bmp"
        if (bytes.matches(intArrayOf(0x49, 0x49, 0x2a, 0x00)) || bytes.matches(intArrayOf(0x4d, 0x4d, 0x00, 0x2a))) {
            return "image/tiff"
        }

        sniffIsoMedia(bytes)?.let { return it }

        if (bytes.ascii(0, 5) == "%PDF-") return "application/pdf"
        if (bytes.matches(intArrayOf(0x50, 0x4b, 0x03, 0x04))) return "application/zip"
        if (bytes.matches(intArrayOf(0x1f, 0x8b))) return "application/gzip"
        if (bytes.matches(intArrayOf(0x52, 0x61, 0x72, 0x21, 0x1a, 0x07))) return "application/vnd.rar"
        if (bytes.matches(intArrayOf(0x37, 0x7a, 0xbc, 0xaf, 0x27, 0x1c))) return "application/x-7z-compressed"
        // An ID3 tag, or a bare MPEG layer III frame header.
        if (bytes.ascii(0, 3) == "ID3" || (bytes.isNotEmpty() && bytes.byteAt(0) == 0xff && (bytes.byteAt(1) and 0xe6) == 0xe2)) {
            return "audio/mpeg"
        }
        if (bytes.ascii(0, 4) == "OggS") return "audio/ogg"
        if (bytes.ascii(0, 4) == "fLaC") return "audio/flac"
        if (bytes.matches(intArrayOf(0x1a, 0x45, 0xdf, 0xa3))) return "video/webm"
        return null
    }

    /** Lower case and without the dot. Hidden files such as ".bashrc" have none. */
    fun extensionOf(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        return if (dot > 0 && dot < fileName.length - 1) fileName.substring(dot + 1).lowercase() else ""
    }

    fun kindOf(mimeType: String): FileKind = when {
        mimeType == "application/pdf" -> FileKind.PDF
        mimeType.startsWith("image/") -> FileKind.IMAGE
        mimeType.startsWith("video/") -> FileKind.VIDEO
        mimeType.startsWith("audio/") -> FileKind.AUDIO
        mimeType in DOCUMENT_TYPES -> FileKind.DOCUMENT
        mimeType in SPREADSHEET_TYPES -> FileKind.SPREADSHEET
        mimeType in PRESENTATION_TYPES -> FileKind.PRESENTATION
        mimeType in ARCHIVE_TYPES -> FileKind.ARCHIVE
        mimeType in CODE_TYPES -> FileKind.CODE
        mimeType.startsWith("text/") -> FileKind.TEXT
        else -> FileKind.OTHER
    }

    private fun normalizeDeclaredType(value: String?): String? {
        val type = value?.substringBefore(';')?.trim()?.lowercase()
        return type?.takeIf { it.isNotEmpty() && it != DEFAULT_TYPE }
    }

    private fun labelFor(mimeType: String, extension: String): String {
        val named = if (extension.isNotEmpty() && EXTENSION_TYPES[extension] == mimeType) {
            extension
        } else {
            PREFERRED_EXTENSION[mimeType]
        }
        if (named != null) return named.uppercase()
        return if (extension.isNotEmpty() && extension.length <= 5) extension.uppercase() else "FILE"
    }

    /**
     * Bytes first, then the extension, then whatever type the platform declared.
     * The declared type comes last because pickers derive it from the extension
     * anyway; it only adds something for a file with no name to go on.
     *
     * [head] may be the whole file: only its first [SNIFF_BYTE_COUNT] bytes are
     * looked at, so a hostile header cannot make this walk 20 MB.
     */
    fun detect(name: String, head: ByteArray? = null, declaredType: String? = null): DetectedFileType {
        val extension = extensionOf(name)
        val fromExtension = EXTENSION_TYPES[extension]
        val sniffed = head
            ?.let { if (it.size > SNIFF_BYTE_COUNT) it.copyOf(SNIFF_BYTE_COUNT) else it }
            ?.let(::sniffMimeType)
        // sniffIsoMedia falls back to video/mp4 for any container it does not
        // recognise, which would relabel an .m4a recording as video. The name is
        // better evidence in that one case.
        val isoFamily = fromExtension.takeIf {
            sniffed == "video/mp4" && (it == "audio/mp4" || it == "video/quicktime" || it == "video/3gpp")
        }
        val zipContainer = fromExtension.takeIf { sniffed == "application/zip" && it in ZIP_CONTAINER_TYPES }

        val mimeType = isoFamily
            ?: zipContainer
            ?: sniffed
            ?: fromExtension
            ?: normalizeDeclaredType(declaredType)
            ?: DEFAULT_TYPE

        return DetectedFileType(mimeType, kindOf(mimeType), labelFor(mimeType, extension))
    }

    /**
     * The kind and label for a file whose type was already worked out, such as
     * one sniffed on arrival - so a misleading extension cannot undo that. A
     * type that says nothing ("application/octet-stream") still falls back to
     * the name.
     */
    fun describe(name: String, mimeType: String?): DetectedFileType =
        if (mimeType != null && mimeType.isNotEmpty() && mimeType != DEFAULT_TYPE) {
            DetectedFileType(mimeType, kindOf(mimeType), labelFor(mimeType, extensionOf(name)))
        } else {
            detect(name)
        }
}
