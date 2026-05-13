package com.redline.viewer.data

sealed class Loadable<out T> {
    object Idle : Loadable<Nothing>()
    object Loading : Loadable<Nothing>()
    data class Ok<T>(val value: T) : Loadable<T>()
    data class Err(val message: String) : Loadable<Nothing>()
}
