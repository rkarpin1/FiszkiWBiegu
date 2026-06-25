@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package pl.rkarpinski.fiszkiwbiegu

// Pobiera APK jako blob i nadaje mu jawnie MIME application/vnd.android.package-archive.
// Bez tego przeglądarki mobilne sniffują zawartość (APK = archiwum ZIP) i zapisują plik jako .zip,
// ignorując atrybut download. Blob (same-origin) honoruje nazwę z atrybutu download. W razie błędu
// fetch (np. blokada) wracamy do bezpośredniego linku.
private fun jsDownloadApk(): JsAny? = js(
    "(function(){var u='https://fiszkiwbiegu.xtaxi.eu/FiszkiWBiegu.apk';function direct(){var a=document.createElement('a');a.href=u;a.download='FiszkiWBiegu.apk';document.body.appendChild(a);a.click();a.remove();}fetch(u).then(function(r){return r.blob();}).then(function(b){var apk=new Blob([b],{type:'application/vnd.android.package-archive'});var o=URL.createObjectURL(apk);var a=document.createElement('a');a.href=o;a.download='FiszkiWBiegu.apk';document.body.appendChild(a);a.click();a.remove();setTimeout(function(){URL.revokeObjectURL(o);},10000);}).catch(direct);return null;})()"
)

actual fun downloadApk() {
    jsDownloadApk()
}
