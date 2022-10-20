package com.geeksville.mesh.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.geeksville.mesh.R
import com.geeksville.mesh.model.map.ChildData
import com.geeksville.mesh.model.map.Constants
import com.geeksville.mesh.model.map.MapParentData

class MapMenuRecyclerAdapter(var mContext: Context, val list: MutableList<MapParentData>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {

        return if (viewType == Constants.PARENT) {
            val rowView: View =
                LayoutInflater.from(parent.context).inflate(R.layout.parent_row, parent, false)
            GroupViewHolder(rowView)
        } else {
            val rowView: View =
                LayoutInflater.from(parent.context).inflate(R.layout.child_row, parent, false)
            ChildViewHolder(rowView)
        }
    }

    override fun getItemCount(): Int = list.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {

        val dataList = list[position]
        if (dataList.type == Constants.PARENT) {
            holder as GroupViewHolder
            holder.apply {
                parentTV?.text = dataList.title
                downIV?.setOnClickListener {
                    expandOrCollapseParentItem(dataList, position)
                }
            }
        } else {
            holder as ChildViewHolder

            holder.apply {
                val singleService = dataList.subList.first()
                childTV?.text = singleService.childTitle
            }
        }
    }

    private fun expandOrCollapseParentItem(singleBoarding: MapParentData, position: Int) {

        if (singleBoarding.isExpanded) {
            collapseParentRow(position)
        } else {
            expandParentRow(position)
        }
    }

    private fun expandParentRow(position: Int) {
        val currentBoardingRow = list[position]
        val services = currentBoardingRow.subList
        currentBoardingRow.isExpanded = true
        var nextPosition = position
        if (currentBoardingRow.type == Constants.PARENT) {

            services.forEach { service ->
                val parentModel = MapParentData()
                parentModel.type = Constants.CHILD
                val subList: ArrayList<ChildData> = ArrayList()
                subList.add(service)
                parentModel.subList = subList
                list.add(++nextPosition, parentModel)
            }
            notifyDataSetChanged()
        }
    }

    private fun collapseParentRow(position: Int) {
        val currentBoardingRow = list[position]
        val services = currentBoardingRow.subList
        list[position].isExpanded = false
        if (list[position].type == Constants.PARENT) {
            services.forEach { _ ->
                list.removeAt(position + 1)
            }
            notifyDataSetChanged()
        }
    }

    override fun getItemViewType(position: Int): Int = list[position].type

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    class GroupViewHolder(row: View) : RecyclerView.ViewHolder(row) {
        val parentTV = row.findViewById(R.id.parent_Title) as TextView?
        val downIV = row.findViewById(R.id.down_iv) as ImageView?
    }

    class ChildViewHolder(row: View) : RecyclerView.ViewHolder(row) {
        val childTV = row.findViewById(R.id.child_Title) as TextView?

    }
}
