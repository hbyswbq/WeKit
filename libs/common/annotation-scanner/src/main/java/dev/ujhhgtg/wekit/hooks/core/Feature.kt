package dev.ujhhgtg.wekit.hooks.core

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Feature(
    val name: String,
    val categories: Array<String>,
    val description: String = ""
)
