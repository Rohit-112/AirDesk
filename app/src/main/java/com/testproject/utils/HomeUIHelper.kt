package com.testproject.utils

import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.testproject.R
import com.testproject.databinding.FragmentHomeBinding

class HomeUIHelper(private val binding: FragmentHomeBinding) {

    fun animateEntrance() {
        binding.layoutStatus.root.slideUp(500, 100)
        binding.layoutTransfer.root.slideUp(500, 200)
        binding.layoutHistory.root.slideUp(500, 300)
    }

    fun updateStatusUI(isConnected: Boolean, isPeerConnected: Boolean) {
        val context = binding.root.context
        val uiState = getUIState(isConnected, isPeerConnected)

        binding.layoutStatus.statusCard.setCardBackgroundColor(ContextCompat.getColor(context, uiState.bgColor))
        binding.layoutStatus.ivStatusIcon.setImageResource(uiState.iconRes)
        binding.layoutStatus.ivStatusIcon.setColorFilter(ContextCompat.getColor(context, uiState.textColor))
        
        binding.layoutStatus.tvStatusText.apply {
            text = context.getString(uiState.textRes)
            setTextColor(ContextCompat.getColor(context, uiState.textColor))
        }
        binding.layoutStatus.tvConnectedTo.setTextColor(ContextCompat.getColor(context, uiState.textColor))

        val showInputs = !(isConnected && isPeerConnected)
        binding.layoutDisconnected.connectCard.visibility = if (showInputs) View.VISIBLE else View.GONE
        binding.layoutDisconnected.codeCard.visibility = if (showInputs) View.VISIBLE else View.GONE

        if (uiState.showTransferCard) {
            if (binding.layoutTransfer.root.visibility != View.VISIBLE) binding.layoutTransfer.root.slideUp()
        } else {
            binding.layoutTransfer.root.visibility = View.GONE
        }

        binding.layoutStatus.btnUnlink.visibility = if (uiState.showUnlinkButton) View.VISIBLE else View.GONE
    }

    fun updateHistoryVisibility(sharedCount: Int, receivedCount: Int) {
        val isSharedEmpty = sharedCount == 0
        val isReceivedEmpty = receivedCount == 0

        binding.layoutHistory.rvSharedItems.visibility = if (isSharedEmpty) View.GONE else View.VISIBLE
        binding.layoutHistory.tvNoShared.visibility = if (isSharedEmpty) View.VISIBLE else View.GONE
        
        binding.layoutHistory.rvReceivedItems.visibility = if (isReceivedEmpty) View.GONE else View.VISIBLE
        binding.layoutHistory.tvNoReceived.visibility = if (isReceivedEmpty) View.VISIBLE else View.GONE

        limitRecyclerViewHeight(binding.layoutHistory.rvSharedItems, sharedCount)
        limitRecyclerViewHeight(binding.layoutHistory.rvReceivedItems, receivedCount)
    }
    
    private fun limitRecyclerViewHeight(recyclerView: RecyclerView, itemCount: Int) {
        val params = recyclerView.layoutParams
        if (itemCount > 3) {
            val density = recyclerView.context.resources.displayMetrics.density
            params.height = (3 * 80 * density).toInt()
        } else {
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        recyclerView.layoutParams = params
    }
    
    fun updateQueueVisibility(isEmpty: Boolean) {
        binding.layoutQueue.root.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun getUIState(isConnected: Boolean, isPeerConnected: Boolean) = when {
        isConnected && isPeerConnected -> HomeUIState(R.color.status_connected_bg, R.color.status_connected_text, R.drawable.connected, R.string.status_connected, false, true, true)
        isConnected -> HomeUIState(android.R.color.holo_orange_light, android.R.color.holo_orange_dark, R.drawable.refresh, R.string.status_waiting, true, false, true)
        else -> HomeUIState(R.color.status_disconnected_bg, R.color.status_disconnected_text, R.drawable.disconnected, R.string.status_not_connected, true, false, false)
    }

    private data class HomeUIState(
        val bgColor: Int,
        val textColor: Int,
        val iconRes: Int,
        val textRes: Int,
        val showDisconnectedLayout: Boolean,
        val showTransferCard: Boolean,
        val showUnlinkButton: Boolean
    )
}
