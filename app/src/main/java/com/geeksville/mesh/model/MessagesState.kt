package com.geeksville.mesh.model

import androidx.compose.mutableStateOf
import com.geeksville.android.Logging
import java.util.*

/**
 * the model object for a text message
 *
 * if errorMessage is set then we had a problem sending this message
 */
data class TextMessage(
    val from: String,
    val text: String,
    val date: Date = Date(),
    val errorMessage: String? = null
)


object MessagesState : Logging {
    val testTexts = listOf(
        TextMessage(
            "+16508765310",
            "I found the cache"
        ),
        TextMessage(
            "+16508765311",
            "Help! I've fallen and I can't get up."
        )
    )

    // If the following (unused otherwise) line is commented out, the IDE preview window works.
    // if left in the preview always renders as empty.
    val messages = mutableStateOf(testTexts, { a, b ->
        a.size == b.size // If the # of messages changes, consider it important for rerender
    })

    fun addMessage(m: TextMessage) {
        val l = messages.value.toMutableList()
        l.add(m)
        messages.value = l
    }
}
