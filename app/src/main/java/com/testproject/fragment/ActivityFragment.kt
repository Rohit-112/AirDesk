package com.testproject.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.testproject.R
import com.testproject.databinding.FragmentActivityBinding

class ActivityFragment : Fragment() {

    private var _binding: FragmentActivityBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentActivityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // When there's no activity, we show the empty state.
        // The button allows the user to navigate back to the Dashboard to connect.
        binding.btnGoHome.setOnClickListener {
            findNavController().navigate(R.id.homeFragment)
        }
        
        // For now, we assume there's no activity and keep the empty state visible.
        // In a real app, you'd check your local database or viewmodel here.
        checkActivity()
    }
    
    private fun checkActivity() {
        // Placeholder for activity check logic.
        // If history exists:
        // binding.layoutEmptyState.visibility = View.GONE
        // binding.rvActivityHistory.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
