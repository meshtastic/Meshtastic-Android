package com.geeksville.mesh.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.geeksville.mesh.R
import com.geeksville.mesh.database.entity.Packet
import java.text.DateFormat
import java.util.*

class PacketListAdapter internal constructor(
    context: Context
) : RecyclerView.Adapter<PacketListAdapter.PacketViewHolder>() {

    private val inflater: LayoutInflater = LayoutInflater.from(context)
    private var packets = emptyList<Packet>()

    private val timeFormat: DateFormat =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)

    inner class PacketViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val packetTypeView: TextView = itemView.findViewById(R.id.type)
        val packetDateReceivedView: TextView = itemView.findViewById(R.id.dateReceived)
        val packetRawMessage: TextView = itemView.findViewById(R.id.rawMessage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PacketViewHolder {
        val itemView = inflater.inflate(R.layout.adapter_packet_layout, parent, false)
        return PacketViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: PacketViewHolder, position: Int) {
        val current = packets[position]
        holder.packetTypeView.text = current.message_type
        holder.packetRawMessage.text = current.raw_message
        val date = Date(current.received_date)
        holder.packetDateReceivedView.text = timeFormat.format(date)
    }

    internal fun setPackets(packets: List<Packet>) {
        this.packets = packets
        notifyDataSetChanged()
    }

    override fun getItemCount() = packets.size

}