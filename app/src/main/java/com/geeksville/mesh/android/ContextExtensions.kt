package com.geeksville.mesh.android

import android.app.Activity
import android.content.Context
import android.view.inputmethod.InputMethodManager
import android.widget.Toast

/// show a toast
fun Context.toast(message: CharSequence) =
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

/// Utility function to hide the soft keyboard per stack overflow
fun Activity.hideKeyboard() {
    // Check if no view has focus:
    currentFocus?.let { v ->
        val imm =
            getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(v.windowToken, 0)
    }
}
