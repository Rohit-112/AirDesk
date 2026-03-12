package com.testproject.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.testproject.R
import com.testproject.data.HistoryItem
import com.testproject.databinding.ItemHistoryActivityBinding
import com.testproject.utils.FileUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private val items = mutableListOf<HistoryItem>()

    fun updateItems(newItems: List<HistoryItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryActivityBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, position == 0, position == items.size - 1)
    }

    override fun getItemCount() = items.size

    class ViewHolder(private val binding: ItemHistoryActivityBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(item: HistoryItem, isFirst: Boolean, isLast: Boolean) {
            val context = binding.root.context
            
            // Status and Color
            if (item.isReceived) {
                binding.tvStatus.text = "Received"
                binding.timelineDot.backgroundTintList = ContextCompat.getColorStateList(context, R.color.status_waiting_text)
            } else {
                binding.tvStatus.text = "Shared"
                binding.timelineDot.backgroundTintList = ContextCompat.getColorStateList(context, R.color.accent)
            }

            // Content & Type Label
            if (item.isFile) {
                val typeLabel = FileUtils.getFileTypeLabel(item.fileName)
                binding.tvContent.text = "[$typeLabel] ${item.fileName}"
                binding.ivAction.setImageResource(FileUtils.getFileIcon(item.fileName))
                binding.ivAction.visibility = View.VISIBLE
            } else {
                binding.tvContent.text = item.content
                binding.ivAction.setImageResource(R.drawable.activity_icon)
                binding.ivAction.visibility = View.GONE
            }
            
            // Time
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            val timeStr = sdf.format(Date(item.timestamp))
            val timeAgo = getTimeAgo(item.timestamp)
            binding.tvTime.text = "$timeStr - $timeAgo"

            binding.ivAction.visibility = if (item.isFile) View.VISIBLE else View.GONE
        }

        private fun getTimeAgo(time: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - time
            
            return when {
                diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
                diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)} mins ago"
                diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)} hours ago"
                else -> "${TimeUnit.MILLISECONDS.toDays(diff)} days ago"
            }
        }
    }
}
