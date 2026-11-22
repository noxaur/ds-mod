package io.github.noxaur.dsmod.edge.input

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class InputApproved(val by: String = "", val hash: String = "", val reaches: Array<String> = [])
