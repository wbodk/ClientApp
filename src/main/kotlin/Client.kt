import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

const val SERVER_URL = "http://127.0.0.1:8080"
const val OUTPUT_FILE = "downloaded.bin"
const val CHUNK_SIZE = 512

fun sha256(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(bytes).joinToString("") { "%02x".format(it) }
}

//fun sha256(filePath: String): String {
//    val digest = MessageDigest.getInstance("SHA-256")
//    File(filePath).inputStream().use { input ->
//        val buffer = ByteArray(8192)
//        var read = input.read(buffer)
//        while (read != -1) {
//            digest.update(buffer, 0, read)
//            read = input.read(buffer)
//        }
//    }
//    return digest.digest().joinToString("") { "%02x".format(it) }
//}

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

fun main(args: Array<String>) {

    val connection = URL(SERVER_URL).openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.inputStream.use { it.readBytes() }
    val totalLength = connection.getHeaderField("Content-Length")?.toIntOrNull()
        ?: throw RuntimeException("Invalid content length")

//    println(String.format("Data size:%20.2f Kb", totalLength/1024.0))

//    val data = ByteArray(totalLength)
    val downloaded = BooleanArray(totalLength)
    var receivedBytes = 0
    val outputFile = RandomAccessFile(File(OUTPUT_FILE), "rw")
    outputFile.setLength(totalLength.toLong())

    while (true) {
        val start = downloaded.indexOfFirst { !it }
        if (start == -1) break

        var end = start
        while (end < totalLength && !downloaded[end] && end - start < CHUNK_SIZE * 1024) {
            end++
        }

        val rangeHeader = "bytes=$start-$end"
        val conn = URL(SERVER_URL).openConnection() as HttpURLConnection
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
//                data[idx] = received[i]
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
//    println("SHA-256: ${sha256(data)}")
    println("SHA-256: ${sha256(OUTPUT_FILE)}")
}
