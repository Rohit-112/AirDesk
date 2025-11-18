package com.testproject.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.testproject.base.BaseFragment
import com.testproject.databinding.FragmentHomeBinding
import com.testproject.service.HandleSendToText
import com.testproject.viewmodel.SharedViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeFragment : BaseFragment() {

    lateinit var binding: FragmentHomeBinding
    private val sharedViewModel: SharedViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        btmNavShow(true)

        sharedViewModel.lastSentText.observe(viewLifecycleOwner) { text ->
            binding.tvClipboardContent.text = text
        }

        // Button to re-send the text
        binding.btnSendClipboard.setOnClickListener {
            sharedViewModel.lastSentText.value?.let { text ->
                HandleSendToText(requireContext(), text)
            }
        }
    }
}
