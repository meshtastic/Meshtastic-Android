package com.geeksville.mesh.ui

import android.view.Gravity
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.PopupMenu
import com.geeksville.mesh.NodeInfo
import com.geeksville.mesh.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

internal fun View.nodeMenu(
    node: NodeInfo,
    ignoreIncomingList: List<Int>,
    isOurNode: Boolean = false,
    isManaged: Boolean = false,
    onMenuItemAction: MenuItem.() -> Unit,
) = PopupMenu(context, this, Gravity.NO_GRAVITY, R.attr.actionOverflowMenuStyle, 0).apply {
    val isIgnored = ignoreIncomingList.contains(node.num)

    inflate(R.menu.menu_nodes)
    menu.apply {
        setGroupVisible(R.id.group_remote, !isOurNode)
        setGroupEnabled(R.id.group_admin, !isManaged)
        findItem(R.id.ignore).apply {
            isEnabled = isIgnored || ignoreIncomingList.size < 3
            isChecked = isIgnored
        }
    }
    setOnMenuItemClickListener { item ->
        when (item.itemId) {
            R.id.remove -> {
                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.remove)
                    .setMessage(R.string.remove_node_text)
                    .setNeutralButton(R.string.cancel) { _, _ -> }
                    .setPositiveButton(R.string.send) { _, _ ->
                        item.onMenuItemAction()
                    }
                    .show()
            }

            R.id.ignore -> {
                val message = if (isIgnored) R.string.ignore_remove else R.string.ignore_add
                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.ignore)
                    .setMessage(context.getString(message, node.user?.longName))
                    .setNeutralButton(R.string.cancel) { _, _ -> }
                    .setPositiveButton(R.string.send) { _, _ ->
                        item.onMenuItemAction()
                    }
                    .show()
                item.isChecked = !item.isChecked
            }

            else -> item.onMenuItemAction()
        }
        true
    }
    show()
}
