package com.geeksville.mesh.util

import androidx.compose.ui.Modifier

/**
 * Conditionally applies the [action] to the receiver [Modifier], if [precondition] is true.
 * Returns the receiver as-is otherwise.
 */
inline fun Modifier.thenIf(precondition: Boolean, action: Modifier.() -> Modifier): Modifier =
    if (precondition) action() else this
