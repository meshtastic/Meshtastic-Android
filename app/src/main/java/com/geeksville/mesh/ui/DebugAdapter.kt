package com.geeksville.mesh.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.geeksville.mesh.R
import com.geeksville.mesh.database.entity.MeshLog
import java.text.DateFormat
import java.util.*

class DebugAdapter internal constructor(
    context: Context
) : RecyclerView.Adapter<DebugAdapter.DebugViewHolder>() {

    private val inflater: LayoutInflater = LayoutInflater.from(context)
    private var logs = emptyList<MeshLog>()

    private val timeFormat: DateFormat =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)

    inner class DebugViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val logTypeView: TextView = itemView.findViewById(R.id.type)
        val logDateReceivedView: TextView = itemView.findViewById(R.id.dateReceived)
        val logRawMessage: TextView = itemView.findViewById(R.id.rawMessage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DebugViewHolder {
        val itemView = inflater.inflate(R.layout.adapter_debug_layout, parent, false)
        return DebugViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: DebugViewHolder, position: Int) {
        val current = logs[position]
        holder.logTypeView.text = current.message_type
        holder.logRawMessage.text = current.raw_message
        val date = Date(current.received_date)
        holder.logDateReceivedView.text = timeFormat.format(date)
    }

    internal fun setLogs(logs: List<MeshLog>) {
        this.logs = logs
        notifyDataSetChanged()
    }

    override fun getItemCount() = logs.size

}