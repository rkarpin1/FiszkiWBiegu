// -------------------------------------------------------------------------------------------------
//   Copyright 2026 (c) Robert Karpiński
// -------------------------------------------------------------------------------------------------

package pl.karpinski.fiszkiwbiegu

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform