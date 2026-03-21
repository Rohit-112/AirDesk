package com.testproject.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.testproject.R
import com.testproject.adapter.HistoryAdapter
import com.testproject.databinding.FragmentActivityBinding
import com.testproject.domain.model.HistoryItem
import com.testproject.utils.ClipboardHelper
import com.testproject.utils.showToast
import com.testproject.viewmodel.HistoryViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ActivityFragment : Fragment() {

    private var _binding: FragmentActivityBinding? = null
    private val binding get() = _binding!!

    private val historyViewModel: HistoryViewModel by viewModels()

    @Inject
    lateinit var clipboardHelper: ClipboardHelper

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
        historyAdapter = HistoryAdapter { item -> handleItemClick(item) }
        binding.rvActivityHistory.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = historyAdapter
        }
    }

    private fun handleItemClick(item: HistoryItem) {
        if (item.isFile) {
            requireContext().showToast("File item: ${item.fileName}")
        } else {
            clipboardHelper.copyToClipboard(item.content)
            requireContext().showToast("Text copied to clipboard")
        }
    }

    private fun observeHistory() {
        historyViewModel.sharedHistory.observe(viewLifecycleOwner) { shared ->
            historyViewModel.receivedHistory.observe(viewLifecycleOwner) { received ->
                val combinedList = (shared + received).sortedByDescending { it.timestamp }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
