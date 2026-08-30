@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package site.addzero.studio.web

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.Promise
import kotlinx.coroutines.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.core.annotation.Single
import site.addzero.studio.workbench.browser.BrowserFile
import site.addzero.studio.workbench.browser.BrowserPort

@Single(binds = [BrowserPort::class])
class BrowserPortAdapter : BrowserPort {
    override val query: String
        get() = browserQuery()

    override val origin: String
        get() = browserOrigin()

    override fun read(key: String): String? = browserStorageRead(key)

    override fun write(key: String, value: String) {
        browserStorageWrite(key, value)
    }

    override fun remove(key: String) {
        browserStorageRemove(key)
    }

    @OptIn(ExperimentalEncodingApi::class)
    override fun download(bytes: ByteArray, fileName: String, contentType: String) {
        browserDownload(Base64.encode(bytes), fileName, contentType)
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun chooseFile(): BrowserFile? {
        val payload = browserChooseFile().await() ?: return null
        val payloadText = browserString(payload)
        val value = Json.parseToJsonElement(payloadText).jsonObject
        return BrowserFile(
            name = value.getValue("name").jsonPrimitive.content,
            contentType = value.getValue("contentType").jsonPrimitive.content,
            bytes = Base64.decode(value.getValue("base64").jsonPrimitive.content),
        )
    }
}

@JsFun("() => globalThis.location?.search ?? ''")
private external fun browserQuery(): String

@JsFun("() => globalThis.location?.origin ?? ''")
private external fun browserOrigin(): String

@JsFun("(key) => globalThis.localStorage?.getItem(key) ?? null")
private external fun browserStorageRead(key: String): String?

@JsFun("(key, value) => globalThis.localStorage?.setItem(key, value)")
private external fun browserStorageWrite(key: String, value: String)

@JsFun("(key) => globalThis.localStorage?.removeItem(key)")
private external fun browserStorageRemove(key: String)

@JsFun(
    """(base64, fileName, contentType) => {
        const binary = atob(base64);
        const bytes = new Uint8Array(binary.length);
        for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
        const url = URL.createObjectURL(new Blob([bytes], { type: contentType }));
        const link = document.createElement('a');
        link.href = url;
        link.download = fileName;
        document.body.appendChild(link);
        link.click();
        link.remove();
        setTimeout(() => URL.revokeObjectURL(url), 0);
    }""",
)
private external fun browserDownload(base64: String, fileName: String, contentType: String)

@JsFun(
    """() => new Promise((resolve) => {
        const input = document.createElement('input');
        input.type = 'file';
        input.onchange = () => {
            const file = input.files && input.files[0];
            if (!file) { resolve(null); return; }
            const reader = new FileReader();
            reader.onload = () => resolve(JSON.stringify({
                name: file.name,
                contentType: file.type || 'application/octet-stream',
                base64: String(reader.result).split(',')[1] || '',
            }));
            reader.onerror = () => resolve(null);
            reader.readAsDataURL(file);
        };
        input.oncancel = () => resolve(null);
        input.click();
    })""",
)
private external fun browserChooseFile(): Promise<JsString?>

@JsFun("(value) => value")
private external fun browserString(value: JsString): String
