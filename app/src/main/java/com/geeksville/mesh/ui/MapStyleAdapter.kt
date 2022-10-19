package com.geeksville.mesh.ui

import android.R
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.geeksville.mesh.databinding.AdapterMapStyleMenuBinding


class MapStyleAdapter(itemView: AdapterMapStyleMenuBinding) {
    var list = mutableListOf<String>()

    inner class MyView(view: View) : RecyclerView.ViewHolder(view) {
        var textView: TextView

        init {
            textView = view.findViewById<View>(R.id.text1) as TextView
        }
    }

    fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): MapStyleAdapter {
        val inflater = LayoutInflater.from(parent.context)
        val mapStyleMenuBinding = AdapterMapStyleMenuBinding.inflate(inflater, parent, false)
        return MapStyleAdapter(mapStyleMenuBinding)
    }

    fun onBindViewHolder(holder: MyView, position: Int) {
        holder.textView.text = list[position]
    }

    fun getItemCount(): Int {
        return list.size
    }
}