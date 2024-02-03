package com.geeksville.mesh.ui

import android.content.Context
import android.text.SpannedString
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.text.toSpannable
import androidx.recyclerview.widget.RecyclerView
import com.geeksville.mesh.R
import com.geeksville.mesh.database.entity.MeshLog
import java.text.DateFormat
import java.util.Date

class DebugAdapter internal constructor(
    context: Context
) : RecyclerView.Adapter<DebugAdapter.DebugViewHolder>() {

    private val inflater: LayoutInflater = LayoutInflater.from(context)
    private val colorAnnotation = ContextCompat.getColor(context, R.color.colorAnnotation)
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
        holder.logRawMessage.text = annotateMessage(current)
        val date = Date(current.received_date)
        holder.logDateReceivedView.text = timeFormat.format(date)
    }

    /**
     * Enhance the raw message by visually distinguishing the annotations prior to when
     * the data was added to the database.
     *
     * @see com.geeksville.mesh.util.MeshProtosExtensionsKt.annotateRawMessage
     */
    private fun annotateMessage(current: MeshLog): CharSequence {
        val spannable = current.raw_message.toSpannable()
        REGEX_ANNOTATED_NODE_ID.findAll(spannable).toList().reversed().forEach {
            spannable.setSpan(
                android.text.style.StyleSpan(android.graphics.Typeface.ITALIC),
                it.range.first,
                it.range.last + 1,
                SpannedString.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                android.text.style.ForegroundColorSpan(colorAnnotation),
                it.range.first,
                it.range.last + 1,
                SpannedString.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return spannable
    }

    internal fun setLogs(logs: List<MeshLog>) {
        this.logs = logs
        notifyDataSetChanged()
    }

    override fun getItemCount() = logs.size

    private companion object {
        /**
         * Regex to match the node ID annotations in the MeshLog raw message text.
         */
        val REGEX_ANNOTATED_NODE_ID = Regex("\\(![0-9a-fA-F]{8}\\)$", RegexOption.MULTILINE)
    }
}