package com.geeksville.mesh.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.MessageStatus
import com.geeksville.mesh.R
import com.geeksville.mesh.databinding.AdapterMessageLayoutBinding
import com.google.android.material.chip.Chip
import java.text.DateFormat
import java.util.*

class MessagesAdapter : RecyclerView.Adapter<MessagesAdapter.ViewHolder>() {

    private var messages = arrayOf<DataPacket>()

    override fun getItemCount(): Int = messages.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val contactViewBinding = AdapterMessageLayoutBinding.inflate(inflater, parent, false)
        return ViewHolder(contactViewBinding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    class ViewHolder(itemView: AdapterMessageLayoutBinding) :
        RecyclerView.ViewHolder(itemView.root) {
        val username: Chip = itemView.username
        val messageText: TextView = itemView.messageText
        val messageTime: TextView = itemView.messageTime
        val messageStatusIcon: ImageView = itemView.messageStatusIcon
        val card: CardView = itemView.Card

        private val dateTimeFormat: DateFormat =
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        private val timeFormat: DateFormat =
            DateFormat.getTimeInstance(DateFormat.SHORT)

        private fun getShortDateTime(time: Date): String {
            // return time if within 24 hours, otherwise date/time
            val one_day = 60 * 60 * 24 * 1000
            if (System.currentTimeMillis() - time.time > one_day) {
                return dateTimeFormat.format(time)
            } else return timeFormat.format(time)
        }

        fun bind(msg: DataPacket) {
            // Determine if this is my message (originated on this device).
            val isMe = msg.from == "^local"

            // Set cardview offset and color.
            val marginParams = card.layoutParams as ViewGroup.MarginLayoutParams
            val messageOffset =
                itemView.context.resources.getDimensionPixelOffset(R.dimen.message_offset)
            if (isMe) {
                marginParams.leftMargin = messageOffset
                marginParams.rightMargin = 0
                itemView.context?.let {
                    card.setCardBackgroundColor(
                        ContextCompat.getColor(
                            it,
                            R.color.colorMyMsg
                        )
                    )
                }
            } else {
                marginParams.rightMargin = messageOffset
                marginParams.leftMargin = 0
                itemView.context?.let {
                    card.setCardBackgroundColor(
                        ContextCompat.getColor(
                            it,
                            R.color.colorMsg
                        )
                    )
                }
            }
            // Hide the username chip for my messages
            if (isMe) {
                username.visibility = View.GONE
            } else {
                username.visibility = View.VISIBLE
//                If we can't find the sender, just use the ID // FIXME - how to get shortName from here?
//                val user = node?.user
//                username.text = user?.shortName ?: msg.from
                username.text = msg.from
            }
            if (msg.errorMessage != null) {
                // FIXME, set the style to show a red error message
                messageText.text = msg.errorMessage
            } else {
                messageText.text = msg.text
            }

            messageTime.text = getShortDateTime(Date(msg.time))

            val icon = when (msg.status) {
                MessageStatus.QUEUED -> R.drawable.ic_twotone_cloud_upload_24
                MessageStatus.DELIVERED -> R.drawable.cloud_on
                MessageStatus.ENROUTE -> R.drawable.ic_twotone_cloud_24
                MessageStatus.ERROR -> R.drawable.cloud_off
                else -> null
            }
            if (icon != null) {
                messageStatusIcon.setImageResource(icon)
                messageStatusIcon.visibility = View.VISIBLE
            } else
                messageStatusIcon.visibility = View.INVISIBLE
        }
    }

    fun onMessagesChanged(msgIn: Collection<DataPacket>) {
        messages = msgIn.toTypedArray()
        notifyDataSetChanged() // FIXME, this is super expensive and redraws all messages

        // scroll to the last line
//        if (itemCount != 0) binding.messageListView.scrollToPosition(itemCount - 1)
    }
}