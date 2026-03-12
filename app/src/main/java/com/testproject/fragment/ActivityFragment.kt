package com.testproject.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.testproject.R
import com.testproject.adapter.HistoryAdapter
import com.testproject.data.HistoryItem
import com.testproject.data.local.HistoryEntity
import com.testproject.data.local.HistoryRepository
import com.testproject.databinding.FragmentActivityBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ActivityFragment : Fragment() {

    private var _binding: FragmentActivityBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var historyRepository: HistoryRepository

    private lateinit var historyAdapter: HistoryAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentActivityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeHistory()
        
        binding.btnGoHome.setOnClickListener {
            findNavController().navigate(R.id.homeFragment)
        }
    }

    private fun setupRecyclerView() {
        historyAdapter = HistoryAdapter()
        binding.rvActivityHistory.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = historyAdapter
        }
    }

    private fun observeHistory() {
        lifecycleScope.launch {
            combine(
                historyRepository.getRecentHistory(isReceived = false),
                historyRepository.getRecentHistory(isReceived = true)
            ) { shared, received ->
                val sharedItems = shared.map { it.toHistoryItem(received = false) }
                val receivedItems = received.map { it.toHistoryItem(received = true) }
                (sharedItems + receivedItems).sortedByDescending { it.timestamp }
            }.collect { combinedList ->
                if (combinedList.isEmpty()) {
                    binding.layoutEmptyState.visibility = View.VISIBLE
                    binding.rvActivityHistory.visibility = View.GONE
                } else {
                    binding.layoutEmptyState.visibility = View.GONE
                    binding.rvActivityHistory.visibility = View.VISIBLE
                    historyAdapter.updateItems(combinedList)
                }
            }
        }
    }

    private fun HistoryEntity.toHistoryItem(received: Boolean) = 
        HistoryItem(id, content, timestamp, isFile, fileName, isReceived = received, isQueued = isQueued)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
