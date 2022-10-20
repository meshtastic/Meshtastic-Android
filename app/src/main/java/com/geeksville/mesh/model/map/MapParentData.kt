package com.geeksville.mesh.model.map

data class MapParentData(
    val title: String? = null,
    var type:Int = Constants.PARENT,
    var subList: MutableList<ChildData> = ArrayList(),
    var isExpanded: Boolean = false
)
