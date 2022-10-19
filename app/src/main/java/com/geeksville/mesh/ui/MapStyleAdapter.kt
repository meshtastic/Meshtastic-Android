package com.geeksville.mesh.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.geeksville.mesh.databinding.AdapterMapMenuSelectionBinding

class MapStyleAdapter(itemView: AdapterMapMenuSelectionBinding) :
    RecyclerView.ViewHolder(itemView.root) {
    val card = itemView.cardview
    val text = itemView.textview1

    val mapStyleAdapater = object : RecyclerView.Adapter<MapStyleAdapter>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MapStyleAdapter {
            val inflater = LayoutInflater.from(parent.context)
            val mapMenuBinding = AdapterMapMenuSelectionBinding.inflate(inflater, parent, false)
            return MapStyleAdapter(mapMenuBinding)
        }

        override fun onBindViewHolder(holder: MapStyleAdapter, position: Int) {
            TODO("Not yet implemented")
        }

        val list = listOf<String>()
        override fun getItemCount() = list.size

    }

    val mapLayerAdapater = object : RecyclerView.Adapter<MapStyleAdapter>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MapStyleAdapter {
            val inflater = LayoutInflater.from(parent.context)
            val mapMenuBinding = AdapterMapMenuSelectionBinding.inflate(inflater, parent, false)
            return MapStyleAdapter(mapMenuBinding)
        }

        override fun onBindViewHolder(holder: MapStyleAdapter, position: Int) {
            TODO("Not yet implemented")
        }

        val list = listOf<String>()
        override fun getItemCount() = list.size
    }
}