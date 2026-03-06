package com.testproject.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.testproject.base.BaseFragment
import com.testproject.data.FirebaseRepository
import com.testproject.databinding.FragmentHomeBinding
import com.testproject.helper.CustomBottomSheetDialog
import com.testproject.network.NetworkUtils
import com.testproject.utils.showToast
import com.testproject.viewmodel.SharedViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeFragment : BaseFragment() {

    private lateinit var binding: FragmentHomeBinding
    private val sharedViewModel: SharedViewModel by activityViewModels()
    private val repo = FirebaseRepository()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        btmNavShow(true)
        setupClickListeners()
        observeViewModel()
        loadPersistedSession()
    }

    private fun setupClickListeners() {
        binding.btnSend.setOnClickListener { switchMode(isHost = true) }
        binding.btnReceive.setOnClickListener { switchMode(isHost = false) }

        binding.sendInclude.btnGenerateCodeSent.setOnClickListener { 
            if (checkNetwork()) generateNewSession() 
        }
        
        binding.receiveInclude.btnJoinSession.setOnClickListener { 
            if (checkNetwork()) joinExistingSession() 
        }
        
        binding.receiveInclude.btnSentItem.setOnClickListener {
            sharedViewModel.lastSentText.value?.let { sharedViewModel.updateText(it) }
        }
    }

    private fun checkNetwork(): Boolean {
        return if (NetworkUtils.isConnected(requireContext())) {
            true
        } else {
            requireContext().showToast("No internet connection")
            false
        }
    }

    private fun observeViewModel() {
        sharedViewModel.connected.observe(viewLifecycleOwner) { isConnected ->
            val status = if (isConnected) "Connected! Syncing..." else "Waiting..."
            binding.sendInclude.tvSessionStatusSent.text = status
            binding.receiveInclude.tvSessionStatusReceived.text = if (isConnected) status else "Disconnected"
        }

        sharedViewModel.sessionCode.observe(viewLifecycleOwner) { code ->
            binding.sendInclude.tvSessionCodeSent.text = code?.let { "Code: $it" } ?: "No Session"
            binding.receiveInclude.tvSessionCodeReceived.text = code?.let { "Joined: $it" } ?: "Not Joined"
        }

        sharedViewModel.lastSentText.observe(viewLifecycleOwner) { text ->
            binding.sendInclude.tvClipboardContentSent.text = text ?: "(Empty)"
        }
    }

    private fun switchMode(isHost: Boolean) {
        binding.modeSelectLayout.visibility = View.GONE
        binding.sendModeLayout.visibility = if (isHost) View.VISIBLE else View.GONE
        binding.receiveModeLayout.visibility = if (isHost) View.GONE else View.VISIBLE
    }

    private fun generateNewSession() {
        repo.createSession { code ->
            if (code != null) {
                persistSession(code, true)
                sharedViewModel.setSession(code, true)
                showSessionSuccessDialog(code)
            } else {
                showErrorDialog("Failed to create session")
            }
        }
    }

    private fun joinExistingSession() {
        val code = binding.receiveInclude.etSessionCode.text.toString().trim()
        if (code.length != 6) {
            binding.receiveInclude.etSessionCode.error = "Invalid Code"
            return
        }

        repo.joinSession(code) { success, error ->
            if (success) {
                persistSession(code, false)
                sharedViewModel.setSession(code, false)
                binding.receiveInclude.etSessionCode.setText("")
                requireContext().showToast("Joined successfully")
            } else {
                showErrorDialog(error ?: "Unknown error")
            }
        }
    }

    private fun showSessionSuccessDialog(code: String) {
        CustomBottomSheetDialog.newInstance(
            title = "Session Created",
            message = "Share this code with the receiver: $code",
            okText = "Got it",
            showCancelButton = false
        ).show(childFragmentManager, "success")
    }

    private fun showErrorDialog(msg: String) {
        CustomBottomSheetDialog.newInstance(
            title = "Error",
            message = msg,
            showCancelButton = false
        ).show(childFragmentManager, "error")
    }

    private fun persistSession(code: String, isHost: Boolean) {
        requireContext().getSharedPreferences("myAppPrefs", Context.MODE_PRIVATE).edit()
            .putString("sessionCode", code)
            .putBoolean("isHost", isHost)
            .apply()
    }

    private fun loadPersistedSession() {
        val prefs = requireContext().getSharedPreferences("myAppPrefs", Context.MODE_PRIVATE)
        val code = prefs.getString("sessionCode", null)
        val isHost = prefs.getBoolean("isHost", true)
        
        if (code != null) {
            sharedViewModel.setSession(code, isHost)
            switchMode(isHost)
        }
    }
}
