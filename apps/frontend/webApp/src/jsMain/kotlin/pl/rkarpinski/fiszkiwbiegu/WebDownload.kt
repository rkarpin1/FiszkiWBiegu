package pl.rkarpinski.fiszkiwbiegu

actual fun downloadApk() {
    js("(function(){var a=document.createElement('a');a.href='https://fiszkiwbiegu.xtaxi.eu/FiszkiWBiegu.apk';a.download='FiszkiWBiegu.apk';document.body.appendChild(a);a.click();a.remove();})()")
}
