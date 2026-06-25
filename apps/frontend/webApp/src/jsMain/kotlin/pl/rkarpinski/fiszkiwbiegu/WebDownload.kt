package pl.rkarpinski.fiszkiwbiegu

actual fun downloadApk() {
    // Pobiera APK jako blob z jawnym MIME application/vnd.android.package-archive — bez tego
    // przeglądarki mobilne sniffują zawartość (APK = archiwum ZIP) i zapisują plik jako .zip,
    // ignorując atrybut download. W razie błędu fetch wracamy do bezpośredniego linku.
    js("(function(){var u='https://fiszkiwbiegu.xtaxi.eu/FiszkiWBiegu.apk';function direct(){var a=document.createElement('a');a.href=u;a.download='FiszkiWBiegu.apk';document.body.appendChild(a);a.click();a.remove();}fetch(u).then(function(r){return r.blob();}).then(function(b){var apk=new Blob([b],{type:'application/vnd.android.package-archive'});var o=URL.createObjectURL(apk);var a=document.createElement('a');a.href=o;a.download='FiszkiWBiegu.apk';document.body.appendChild(a);a.click();a.remove();setTimeout(function(){URL.revokeObjectURL(o);},10000);}).catch(direct);})()")
}
