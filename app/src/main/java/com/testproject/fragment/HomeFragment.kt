package com.testproject.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.testproject.base.BaseFragment
import com.testproject.databinding.FragmentHomeBinding
import com.testproject.helper.CustomBottomSheetDialog
import com.testproject.service.HandleSendToText
import com.testproject.viewmodel.SharedViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeFragment : BaseFragment() {

    private lateinit var binding: FragmentHomeBinding
    private val sharedViewModel: SharedViewModel by activityViewModels()

    private val TAG = "HomeFragmentLogs"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "onCreateView: inflating layout")
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated: fragment UI ready")
        btmNavShow(true)
        setupModeButtons()
        setupSendModeEvents()
        setupReceiveModeEvents()
        loadExistingSession()
    }

    private fun setupModeButtons() {
        binding.btnSend.setOnClickListener { switchToSendMode() }
        binding.btnReceive.setOnClickListener { switchToReceiveMode() }
    }

    private fun switchToSendMode() {
        binding.modeSelectLayout.visibility = View.GONE
        binding.sendModeLayout.visibility = View.VISIBLE
        binding.receiveModeLayout.visibility = View.GONE
    }

    private fun switchToReceiveMode() {
        binding.modeSelectLayout.visibility = View.GONE
        binding.sendModeLayout.visibility = View.GONE
        binding.receiveModeLayout.visibility = View.VISIBLE
    }

    private fun setupSendModeEvents() {
        val send = binding.sendInclude

        send.btnGenerateCodeSent.setOnClickListener { generateNewSession() }

        sharedViewModel.lastSentText.observe(viewLifecycleOwner) { text ->
            send.tvClipboardContentSent.text = text ?: "(Nothing copied yet)"
        }

        send.btnSendClipboardSent.setOnClickListener {
            val text = sharedViewModel.lastSentText.value
            if (text.isNullOrEmpty()) return@setOnClickListener
            HandleSendToText(requireContext(), text)
            sharedViewModel.updateText(text)
        }
    }

    private fun loadExistingSession() {
        val prefs = requireContext().getSharedPreferences(
            "myAppPrefs",
            android.content.Context.MODE_PRIVATE
        )
        val code = prefs.getString("sessionCode", null)
        if (!code.isNullOrEmpty()) {
            binding.sendInclude.tvSessionCodeSent.text = "Session Code: $code"
            binding.sendInclude.tvSessionStatusSent.text = "Waiting for another device..."
            val isHost = prefs.getBoolean("isHost", true)
            sharedViewModel.setSession(code, isHost)
        }
    }

    private fun generateNewSession() {
        val rootRef =
            com.google.firebase.database.FirebaseDatabase.getInstance().getReference("sessions")

        fun tryGenerate() {
            val code = (100000..999999).random().toString()
            rootRef.child(code).get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) tryGenerate() else saveNewSession(code)
            }.addOnFailureListener {
                CustomBottomSheetDialog.newInstance(
                    "Error",
                    "Could not create session: ${it.message}",
                    "retry",
                    "Close",
                    true,
                    true
                )
                    .show(childFragmentManager, "Error")
            }
        }
        tryGenerate()
    }

    private fun saveNewSession(code: String) {
        requireContext().getSharedPreferences("myAppPrefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("sessionCode", code)
            .putBoolean("isHost", true)
            .apply()
        binding.sendInclude.tvSessionCodeSent.text = "Session Code: $code"
        binding.sendInclude.tvSessionStatusSent.text = "Waiting for another device..."
        sharedViewModel.setSession(code, true)
    }

    private fun setupReceiveModeEvents() {
        binding.receiveInclude.btnJoinSession.setOnClickListener {
            val code = binding.receiveInclude.etSessionCode.text.toString().trim()
            if (code.length == 6) joinSession(code) else binding.receiveInclude.etSessionCode.error =
                "Invalid Code"
        }

        binding.receiveInclude.btnSentItem.setOnClickListener {
            val text = sharedViewModel.lastSentText.value
            val code = getSessionCode()
            if (text.isNullOrEmpty() || code.isNullOrEmpty()) return@setOnClickListener
            sharedViewModel.updateText(text)
        }
    }

    private fun joinSession(code: String) {
        requireContext().getSharedPreferences("myAppPrefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("sessionCode", code)
            .putBoolean("isHost", false)
            .apply()
        binding.receiveInclude.etSessionCode.setText("")
        sharedViewModel.setSession(code, false)
    }

    private fun getSessionCode(): String? {
        return requireContext().getSharedPreferences(
            "myAppPrefs",
            android.content.Context.MODE_PRIVATE
        )
            .getString("sessionCode", null)
    }
}
