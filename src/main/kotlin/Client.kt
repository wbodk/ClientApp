import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import java.security.MessageDigest
import kotlin.system.exitProcess

const val DEFAULT_URL = "http://127.0.0.1:8080"
const val DEFAULT_OUTPUT = "downloaded.bin"
const val DEFAULT_CHUNK_SIZE_KB = 1024

fun main(args: Array<String>) {
    val parsedArgs = parseArgs(args)
    val serverURL = parsedArgs["url"] ?: DEFAULT_URL
    val outputFileName = parsedArgs["output"] ?: DEFAULT_OUTPUT
    val chunkSizeKB = parsedArgs["chunk-size"]?.toIntOrNull() ?: DEFAULT_CHUNK_SIZE_KB

    println("Using server: $serverURL")
    println("Output file: $outputFileName")
    println("Chunk size: $chunkSizeKB KB\n")

    val contentLength = getContentLength(serverURL) ?: return

    downloadFile(serverURL, outputFileName, chunkSizeKB, contentLength)

    println("\nDownload complete.")
    println("SHA-256: ${sha256(outputFileName)}")
}

fun downloadFile(
    serverURL: String,
    outputFileName: String,
    chunkSizeKB: Int,
    totalSize: Int
) {
    var receivedBytes = 0
    val chunkSize = chunkSizeKB * 1024
    val outputFile = RandomAccessFile(outputFileName, "rw")
    outputFile.setLength(totalSize.toLong())

    while (receivedBytes < totalSize) {
        val end = (receivedBytes + chunkSize).coerceAtMost(totalSize)
        val rangeHeader = "bytes=$receivedBytes-$end"

        try {
            val conn = URL(serverURL).openConnection() as HttpURLConnection
            conn.setRequestProperty("Range", rangeHeader)
            conn.connect()

            val chunk = conn.inputStream.use { it.readBytes() }
            outputFile.seek(receivedBytes.toLong())
            outputFile.write(chunk)

            receivedBytes += chunk.size

            val percent = receivedBytes / totalSize.toFloat() * 100
            println(String.format("%.2f%%\t(%.2f / %.2f KB)", percent, receivedBytes / 1024.0, totalSize / 1024.0))
        } catch (e: Exception) {
            println("Error downloading chunk [$rangeHeader]: ${e.message}")
            break
        }
    }

    outputFile.close()
}

fun getContentLength(serverURL: String): Int? {
    return try {
        val connection = URL(serverURL).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connect()

        when (connection.responseCode) {
            200, 206 -> connection.getHeaderField("Content-Length")?.toIntOrNull()
            else -> {
                println("Server error: ${connection.responseCode}")
                null
            }
        }
    } catch (e: UnknownHostException) {
        println("Server not found: ${e.message}")
        null
    } catch (e: IOException) {
        println("Could not connect to server: ${e.message}")
        null
    } catch (e: Exception) {
        println("Unexpected error: ${e.message}")
        null
    }
}

fun sha256(filePath: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(8192)
    File(filePath).inputStream().use { input ->
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun parseArgs(args: Array<String>): Map<String, String> {
    if (args.contains("--help")) {
        printHelp()
        exitProcess(0)
    }

    return args.mapNotNull {
        val split = it.split("=", limit = 2)
        if (split.size == 2 && split[0].startsWith("--")) {
            split[0].removePrefix("--") to split[1]
        } else null
    }.toMap()
}

fun printHelp() {
    println(
        """
        Options:
          --url=URL            The server URL to download from (default: $DEFAULT_URL)
          --output=FILE        The output file name (default: $DEFAULT_OUTPUT)
          --chunk-size=KB      Chunk size in kilobytes (default: $DEFAULT_CHUNK_SIZE_KB)
          --help               Show this help message
        """.trimIndent()
    )
}