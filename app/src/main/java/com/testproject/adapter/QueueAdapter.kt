package com.testproject.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.testproject.domain.model.HistoryItem
import com.testproject.databinding.ItemQueueBinding
import com.testproject.utils.FileUtils
import com.testproject.utils.addClickAnimation
import com.testproject.utils.slideUp

class QueueAdapter(private val onShareClick: (HistoryItem) -> Unit) : RecyclerView.Adapter<QueueAdapter.ViewHolder>() {

    private val items = mutableListOf<HistoryItem>()
    private var lastPosition = -1

    fun updateItems(newItems: List<HistoryItem>) {
        items.clear()
        items.addAll(newItems)
        lastPosition = -1
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemQueueBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
        
        val currentPosition = holder.bindingAdapterPosition
        if (currentPosition > lastPosition) {
            // Modern staggered slide-up with subtle delay
            holder.itemView.slideUp(
                duration = 500, 
                startDelay = (currentPosition * 40L).coerceAtMost(400L)
            )
            lastPosition = currentPosition
        }
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(private val binding: ItemQueueBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            // iOS-like tactile feedback
            binding.root.addClickAnimation()
            binding.btnShareNow.addClickAnimation()
        }

        fun bind(item: HistoryItem) {
            if (item.isFile) {
                val typeLabel = FileUtils.getFileTypeLabel(item.fileName)
                binding.tvContent.text = "[$typeLabel] ${item.fileName}"
                binding.ivTypeIcon.setImageResource(FileUtils.getFileIcon(item.fileName))
            } else {
                binding.tvContent.text = item.content
                binding.ivTypeIcon.setImageResource(com.testproject.R.drawable.activity_icon)
            }
            
            binding.btnShareNow.setOnClickListener { onShareClick(item) }
            binding.root.setOnClickListener { onShareClick(item) }
        }
    }
}
