package com.geeksville.mesh.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.Composable
import androidx.ui.core.setContent
import com.geeksville.android.Logging

fun androidx.fragment.app.Fragment.setComposable(
    id: Int,
    content: @Composable() () -> Unit
): View? =
    context?.let {
        FrameLayout(it).apply {
            this.id =
                id // Compose requires a unique ID for the containing view to make savedInstanceState work

            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setContent(content)
        }
    }


class ComposeFragment(screenName: String, id: Int, private val content: @Composable() () -> Unit) :
    ScreenFragment(screenName),
    Logging {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? =
        setComposable(id, content)
}