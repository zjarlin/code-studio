package site.addzero.studio.workbench.browser

interface BrowserPort {
    val query: String
    val origin: String

    fun read(key: String): String?

    fun write(key: String, value: String)

    fun remove(key: String)

    fun download(bytes: ByteArray, fileName: String, contentType: String)

    suspend fun chooseFile(): BrowserFile?
}

data class BrowserFile(
    val name: String,
    val contentType: String,
    val bytes: ByteArray,
)
