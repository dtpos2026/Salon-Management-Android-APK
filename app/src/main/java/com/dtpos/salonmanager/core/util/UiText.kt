package com.dtpos.salonmanager.core.util

/**
 * Text that ViewModels hand to the UI without needing a Context. Resource-backed values are
 * resolved in the presentation layer so every user-facing string stays translatable (Urdu-ready).
 */
sealed interface UiText {
    data class Res(val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Raw(val value: String) : UiText

    companion object {
        fun res(id: Int, vararg args: Any): UiText = Res(id, args.toList())
    }
}
