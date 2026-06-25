@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package pl.rkarpinski.fiszkiwbiegu

// Bezpośredni link (same-origin). NIE używamy blob/createObjectURL — Safe Browsing
// (Chrome/Edge) flaguje pliki .apk z blob: jako "może być szkodliwy" i potrafi je blokować.
// Poprawny MIME (application/vnd.android.package-archive) + Content-Disposition ustawia
// serwer nginx hostujący plik, więc przeglądarka zapisuje go jako .apk, a nie .zip.
private fun jsDownloadApk(): JsAny? = js(
    "(function(){var a=document.createElement('a');a.href='https://fiszkiwbiegu.xtaxi.eu/FiszkiWBiegu.apk';a.download='FiszkiWBiegu.apk';document.body.appendChild(a);a.click();a.remove();return null;})()"
)

actual fun downloadApk() {
    jsDownloadApk()
}
