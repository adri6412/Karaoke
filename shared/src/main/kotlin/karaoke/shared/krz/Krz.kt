package karaoke.shared.krz

import karaoke.shared.model.KrzManifest
import karaoke.shared.model.LyricsDoc
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

const val MANIFEST_ENTRY = "manifest.json"

/** Contenuto completo di un pacchetto .krz caricato in memoria. */
data class KrzPackage(
    val manifest: KrzManifest,
    val lyrics: LyricsDoc,
    val audioBytes: ByteArray,
    val coverBytes: ByteArray? = null
)

/** Pacchetto .krz estratto su disco: il media resta un file (non caricato in memoria). */
data class KrzExtracted(
    val manifest: KrzManifest,
    val lyrics: LyricsDoc,
    val mediaFile: File,
    val coverFile: File? = null
)

class KrzFormatException(message: String) : Exception(message)

/** Lettura/scrittura del contenitore proprietario .krz (archivio ZIP). */
object Krz {

    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun write(out: OutputStream, pkg: KrzPackage) {
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(json.encodeToString(pkg.manifest).encodeToByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(pkg.manifest.lyricsFile))
            zip.write(json.encodeToString(pkg.lyrics).encodeToByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(pkg.manifest.audioFile))
            zip.write(pkg.audioBytes)
            zip.closeEntry()

            val cover = pkg.coverBytes
            val coverName = pkg.manifest.cover
            if (cover != null && coverName != null) {
                zip.putNextEntry(ZipEntry(coverName))
                zip.write(cover)
                zip.closeEntry()
            }
        }
    }

    fun write(file: File, pkg: KrzPackage) = file.outputStream().buffered().use { write(it, pkg) }

    fun read(input: InputStream): KrzPackage {
        val entries = HashMap<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val manifestBytes = entries[MANIFEST_ENTRY]
            ?: throw KrzFormatException("manifest.json mancante nel file .krz")
        val manifest = json.decodeFromString<KrzManifest>(manifestBytes.decodeToString())

        val lyrics = entries[manifest.lyricsFile]?.let {
            json.decodeFromString<LyricsDoc>(it.decodeToString())
        } ?: LyricsDoc()

        val audio = entries[manifest.audioFile]
            ?: throw KrzFormatException("audio '${manifest.audioFile}' mancante nel file .krz")

        val cover = manifest.cover?.let { entries[it] }
        return KrzPackage(manifest, lyrics, audio, cover)
    }

    fun read(file: File): KrzPackage = file.inputStream().buffered().use { read(it) }

    /** Legge solo i metadati (manifest) senza caricare audio/testo: utile per l'indicizzazione. */
    fun readManifest(file: File): KrzManifest = file.inputStream().buffered().use { input ->
        ZipInputStream(input).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                if (entry.name == MANIFEST_ENTRY) {
                    return json.decodeFromString<KrzManifest>(zip.readBytes().decodeToString())
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        throw KrzFormatException("manifest.json mancante nel file .krz")
    }

    /**
     * Estrae il .krz su disco: manifest e testo vengono parsati, il media resta un file.
     * Streaming a memoria costante — adatto a video grandi su dispositivi con poca RAM.
     */
    fun extractTo(src: File, dir: File): KrzExtracted {
        dir.mkdirs()
        val files = HashMap<String, File>()
        src.inputStream().buffered().use { input ->
            ZipInputStream(input).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val out = File(dir, File(entry.name).name)
                        out.outputStream().buffered().use { zip.copyTo(it) }
                        files[entry.name] = out
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        val manifestFile = files[MANIFEST_ENTRY]
            ?: throw KrzFormatException("manifest.json mancante nel file .krz")
        val manifest = json.decodeFromString<KrzManifest>(manifestFile.readText())
        val lyrics = files[manifest.lyricsFile]?.let {
            json.decodeFromString<LyricsDoc>(it.readText())
        } ?: LyricsDoc()
        val media = files[manifest.audioFile]
            ?: throw KrzFormatException("media '${manifest.audioFile}' mancante nel file .krz")
        val cover = manifest.cover?.let { files[it] }
        return KrzExtracted(manifest, lyrics, media, cover)
    }

    /**
     * Scrive un .krz prendendo il media da un file in streaming (senza caricarlo in memoria).
     * Il media viene archiviato senza ricompressione (è già compresso, es. mp4/mp3).
     */
    fun writeStreaming(
        dest: File,
        manifest: KrzManifest,
        lyrics: LyricsDoc,
        mediaFile: File,
        coverFile: File? = null
    ) {
        ZipOutputStream(dest.outputStream().buffered()).use { zip ->
            zip.setLevel(Deflater.DEFAULT_COMPRESSION)
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(json.encodeToString(manifest).encodeToByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(manifest.lyricsFile))
            zip.write(json.encodeToString(lyrics).encodeToByteArray())
            zip.closeEntry()

            zip.setLevel(Deflater.NO_COMPRESSION)
            zip.putNextEntry(ZipEntry(manifest.audioFile))
            mediaFile.inputStream().buffered().use { it.copyTo(zip) }
            zip.closeEntry()

            if (coverFile != null && manifest.cover != null) {
                zip.setLevel(Deflater.DEFAULT_COMPRESSION)
                zip.putNextEntry(ZipEntry(manifest.cover))
                coverFile.inputStream().buffered().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }
}
