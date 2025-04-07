import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.system.exitProcess

fun sha256(filePath: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(8192)
    val input = File(filePath).inputStream()
    var read: Int
    while (input.read(buffer).also { read = it } != -1) {
        digest.update(buffer, 0, read)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun printHelp() {
    println(
        """
        Options:
          --url=URL            The server URL to download from (default: http://127.0.0.1:8080)
          --output=FILE        The output file name (default: downloaded.bin)
          --chunk-size=KB      Chunk size in kilobytes (default: 1024)
          --help               Show this help message and exit
        """.trimIndent()
    )
}

fun parseArgs(args: Array<String>): Map<String, String> {
    if (args.any { it == "--help" }) {
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

fun main(args: Array<String>) {
    val parsedArgs = parseArgs(args)
    val serverURL = parsedArgs["url"] ?: "http://127.0.0.1:8080"
    val outputFileName = parsedArgs["output"] ?: "downloaded.bin"
    val chunkSize = parsedArgs["chunk-size"]?.toIntOrNull() ?: 1024

    println("Using server: $serverURL")
    println("Output file: $outputFileName")
    println("Chunk size: $chunkSize KB\n")

    val connection = URL(serverURL).openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.inputStream.use { it.readBytes() }
    val totalLength = connection.getHeaderField("Content-Length")?.toIntOrNull()
        ?: throw RuntimeException("Invalid content length")

    val downloaded = BooleanArray(totalLength)
    var receivedBytes = 0
    val outputFile = RandomAccessFile(File(outputFileName), "rw")
    outputFile.setLength(totalLength.toLong())

    while (true) {
        val start = downloaded.indexOfFirst { !it }
        if (start == -1) break

        var end = start
        while (end < totalLength && !downloaded[end] && end - start < chunkSize * 1024) {
            end++
        }

        val rangeHeader = "bytes=$start-$end"
        val conn = URL(serverURL).openConnection() as HttpURLConnection
        conn.setRequestProperty("Range", rangeHeader)
        conn.connect()

        if (conn.responseCode != 206 && conn.responseCode != 200) {
            println("Server error: ${conn.responseCode}")
            continue
        }

        val received = conn.inputStream.readBytes()
        for (i in received.indices) {
            val idx = start + i
            if (idx < totalLength) {
                downloaded[idx] = true
            }
        }
        outputFile.write(received)

        receivedBytes += received.size

        val percent = receivedBytes / totalLength.toFloat() * 100.0
        val receivedKB = received.size / 1024.0
        val totalKB = totalLength / 1024.0

        println(String.format("%-7.2fKb / %-7.2fKb\t%.2f %%", receivedKB, totalKB, percent))
    }

    println("\nDone!")
    println("SHA-256: ${sha256(outputFileName)}")
}
