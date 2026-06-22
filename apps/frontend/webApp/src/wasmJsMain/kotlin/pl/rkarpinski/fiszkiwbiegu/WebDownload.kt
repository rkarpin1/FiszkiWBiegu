@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package pl.rkarpinski.fiszkiwbiegu

private fun jsDownloadApk(): JsAny? = js("(function(){var a=document.createElement('a');a.href='https://fiszkiwbiegu.xtaxi.eu/FiszkiWBiegu.apk';a.download='FiszkiWBiegu.apk';document.body.appendChild(a);a.click();a.remove();return null;})()")

actual fun downloadApk() {
    jsDownloadApk()
}
