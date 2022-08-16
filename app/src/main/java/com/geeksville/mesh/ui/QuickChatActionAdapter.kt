package com.geeksville.mesh.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.geeksville.mesh.R
import com.geeksville.mesh.database.entity.QuickChatAction

class QuickChatActionAdapter internal constructor(
    context: Context,
    private val onEdit: (action: QuickChatAction) -> Unit,
    private val repositionAction: (fromPos: Int, toPos: Int) -> Unit,
    private val commitAction: () -> Unit,
) : RecyclerView.Adapter<QuickChatActionAdapter.ActionViewHolder>(), DragManageAdapter.SwapAdapter {

    private val inflater: LayoutInflater = LayoutInflater.from(context)
    private var actions = emptyList<QuickChatAction>()

    inner class ActionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val actionName: TextView = itemView.findViewById(R.id.quickChatActionName)
        val actionValue: TextView = itemView.findViewById(R.id.quickChatActionValue)
        val actionEdit: View = itemView.findViewById(R.id.quickChatActionEdit)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActionViewHolder {
        val itemView = inflater.inflate(R.layout.adapter_quick_chat_action_layout, parent, false)
        return ActionViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ActionViewHolder, position: Int) {
        val current = actions[position]
        holder.actionName.text = current.name
        holder.actionValue.text = current.message
        holder.actionEdit.setOnClickListener {
            onEdit(current)
        }
    }


    internal fun setActions(actions: List<QuickChatAction>) {
        this.actions = actions
        notifyDataSetChanged()
    }

    override fun getItemCount() = actions.size

    override fun swapItems(fromPosition: Int, toPosition: Int) {
        repositionAction(fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
    }

    override fun commitSwaps() {
        commitAction()
    }

}