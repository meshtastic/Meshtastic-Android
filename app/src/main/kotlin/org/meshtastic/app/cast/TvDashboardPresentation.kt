/*
 * Copyright (c) 2026 Meshtastic LLC
 */
package org.meshtastic.app.cast

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.view.Gravity
import android.graphics.Color
import android.view.ViewGroup
import android.widget.ScrollView
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.model.Message
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class TvDashboardPresentation(
    outerContext: Context,
    display: Display
) : Presentation(outerContext, display) {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var container: LinearLayout
    private lateinit var headerStats: TextView
    private lateinit var messageContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private var scrollJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212")) // Dark theme
            setPadding(20, 20, 20, 20)
        }

        // Header Section
        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 10)
        }

        val leftHeader = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        
        val title = TextView(context).apply {
            text = "Meshtastic Mesh Overview"
            setTextColor(Color.WHITE)
            textSize = 22f
        }
        leftHeader.addView(title)

        headerStats = TextView(context).apply {
            text = "Loading nodes..."
            setTextColor(Color.LTGRAY)
            textSize = 14f
        }
        leftHeader.addView(headerStats)
        
        headerLayout.addView(leftHeader, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        // Right side: LongFast Messages
        messageContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        headerLayout.addView(messageContainer, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f))

        root.addView(headerLayout)

        // Scrollable Node List
        scrollView = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
        }
        container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(container)
        root.addView(scrollView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        setContentView(root)

        setupDataCollection()
        startAutoScroll()
    }

    private fun startAutoScroll() {
        scrollJob?.cancel()
        scrollJob = scope.launch {
            while (isActive) {
                delay(50)
                val maxScroll = container.height - scrollView.height
                if (maxScroll > 0) {
                    var newScroll = scrollView.scrollY + 2
                    if (newScroll >= maxScroll) {
                        delay(2000) // Pause at bottom
                        newScroll = 0
                        scrollView.scrollTo(0, 0)
                        delay(2000) // Pause at top
                    } else {
                        scrollView.scrollTo(0, newScroll)
                    }
                }
            }
        }
    }

    private fun setupDataCollection() {
        val nodeRepository: NodeRepository = org.koin.mp.KoinPlatform.getKoin().get()
        val packetRepository: PacketRepository = org.koin.mp.KoinPlatform.getKoin().get()

        // Observe Nodes
        nodeRepository.nodeDBbyNum.onEach { nodes ->
            val nodeList = nodes.values.toList()
                .filter { !it.isIgnored }
                .sortedBy { it.user.long_name }
            
            updateUi(nodeList)
        }.launchIn(scope)

        // Observe Channel 0 Messages
        scope.launch {
            packetRepository.getMessagesFrom(
                contact = "0^all",
                limit = 5,
                includeFiltered = false,
                getNode = { id -> nodesByNum().values.find { it.user.id == id } ?: org.meshtastic.core.model.Node(num = 0) }
            ).collect { messages ->
                updateMessages(messages)
            }
        }
    }

    private fun nodesByNum(): Map<Int, org.meshtastic.core.model.Node> {
        val repo: NodeRepository = org.koin.mp.KoinPlatform.getKoin().get()
        return repo.nodeDBbyNum.value
    }

    private fun updateMessages(messages: List<Message>) {
        messageContainer.removeAllViews()
        
        // Add a label
        val label = TextView(context).apply {
            text = "RECENT MESSAGES (LongFast)"
            setTextColor(Color.parseColor("#444444"))
            textSize = 10f
            gravity = Gravity.END
            setPadding(0, 0, 0, 4)
        }
        messageContainer.addView(label)

        messages.forEach { msg ->
            val msgView = TextView(context).apply {
                val sender = msg.node.user.short_name.ifBlank { msg.node.user.id }
                text = "$sender: ${msg.text}"
                setTextColor(Color.CYAN)
                textSize = 11f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = Gravity.END
            }
            messageContainer.addView(msgView)
        }
    }

    private fun updateUi(nodes: List<org.meshtastic.core.model.Node>) {
        headerStats.text = "Total Nodes: ${nodes.size}"
        
        container.removeAllViews()
        
        // Use a 2-column approach by grouping nodes in pairs
        for (i in nodes.indices step 2) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                val rowParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                layoutParams = rowParams
            }

            // First Column
            row.addView(createNodeView(nodes[i]), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(5, 5, 5, 5)
            })

            // Second Column (if available)
            if (i + 1 < nodes.size) {
                row.addView(createNodeView(nodes[i + 1]), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(5, 5, 5, 5)
                })
            } else {
                // Spacer for single item rows
                row.addView(android.view.View(context), LinearLayout.LayoutParams(0, 1, 1f))
            }

            container.addView(row)
        }
    }

    private fun createNodeView(node: org.meshtastic.core.model.Node): LinearLayout {
        val nodeView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(15, 15, 15, 15)
            setBackgroundColor(Color.parseColor("#1E1E1E"))
        }

        val nameView = TextView(context).apply {
            text = node.user.long_name.ifBlank { node.user.id }
            setTextColor(Color.WHITE)
            textSize = 18f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        nodeView.addView(nameView)

        val detailView = TextView(context).apply {
            val snr = if (node.snr > 1000) "N/A" else "${node.snr}"
            val batt = if (node.deviceMetrics.battery_level == 0) "N/A" else "${node.deviceMetrics.battery_level}%"
            text = "ID: ${node.user.id}  |  SNR: $snr  |  Batt: $batt"
            setTextColor(Color.GREEN)
            textSize = 13f
        }
        nodeView.addView(detailView)
        
        return nodeView
    }

    override fun onStop() {
        super.onStop()
        scrollJob?.cancel()
        scope.cancel()
    }
}
