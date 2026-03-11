package com.testproject.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.testproject.R
import com.testproject.base.BaseFragment
import com.testproject.data.FirebaseRepository
import com.testproject.databinding.FragmentHomeBinding
import com.testproject.helper.CustomBottomSheetDialog
import com.testproject.network.NetworkUtils
import com.testproject.utils.AppPreference
import com.testproject.utils.showToast
import com.testproject.viewmodel.SharedViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : BaseFragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    
    private val sharedViewModel: SharedViewModel by activityViewModels()
    
    @Inject
    lateinit var repo: FirebaseRepository

    @Inject
    lateinit var appPreference: AppPreference

    private val deviceId: String by lazy {
        @SuppressLint("HardwareIds")
        Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ANDROID_ID)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        btmNavShow(true)
        setupRecyclerViews()
        setupClickListeners()
        observeViewModel()
        loadPersistedSession()
    }

    private fun setupRecyclerViews() {
        binding.rvSharedItems.layoutManager = LinearLayoutManager(requireContext())
        binding.rvReceivedItems.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun setupClickListeners() {
        binding.btnConnect.setOnClickListener { 
            if (checkNetwork()) joinExistingSession() 
        }

        binding.btnRefreshCode.setOnClickListener {
            if (checkNetwork()) {
                val oldCode = sharedViewModel.sessionCode.value
                if (oldCode != null) {
                    repo.deleteSession(oldCode)
                }
                generateNewSession()
            }
        }

        binding.btnUnlink.setOnClickListener {
            unlinkSession()
        }

        binding.btnShareText.setOnClickListener {
            shareCustomText()
        }

        binding.btnSelectFiles.setOnClickListener {
            requireContext().showToast("File selection coming soon")
        }
    }

    private fun shareCustomText() {
        val text = binding.etShareText.text.toString().trim()
        if (text.isNotEmpty()) {
            sharedViewModel.updateText(text)
            binding.etShareText.setText("")
            requireContext().showToast("Text shared!")
        } else {
            requireContext().showToast("Please enter some text")
        }
    }

    private fun checkNetwork(): Boolean {
        return if (NetworkUtils.isConnected(requireContext())) {
            true
        } else {
            requireContext().showToast(getString(R.string.no_internet))
            false
        }
    }

    private fun observeViewModel() {
        sharedViewModel.connected.observe(viewLifecycleOwner) { 
            updateStatusUI() 
        }
        sharedViewModel.peerConnected.observe(viewLifecycleOwner) { 
            updateStatusUI() 
        }

        sharedViewModel.sessionCode.observe(viewLifecycleOwner) { code ->
            binding.tvYourCode.text = code ?: "------"
            binding.tvConnectedTo.text = if (code != null) {
                getString(R.string.connected_to, code)
            } else {
                getString(R.string.status_not_connected)
            }
        }
    }

    private fun updateStatusUI() {
        val isConnected = sharedViewModel.connected.value ?: false
        val isPeerConnected = sharedViewModel.peerConnected.value ?: false

        val uiState = getUIState(isConnected, isPeerConnected)

        binding.statusCard.setCardBackgroundColor(ContextCompat.getColor(requireContext(), uiState.bgColor))
        binding.ivStatusIcon.setImageResource(uiState.iconRes)
        binding.ivStatusIcon.setColorFilter(ContextCompat.getColor(requireContext(), uiState.textColor))
        binding.tvStatusText.text = getString(uiState.textRes)
        binding.tvStatusText.setTextColor(ContextCompat.getColor(requireContext(), uiState.textColor))
        binding.tvConnectedTo.setTextColor(ContextCompat.getColor(requireContext(), uiState.textColor))

        binding.layoutDisconnected.visibility = if (uiState.showDisconnectedLayout) View.VISIBLE else View.GONE
        binding.transferCard.visibility = if (uiState.showTransferCard) View.VISIBLE else View.GONE
        binding.btnUnlink.visibility = if (uiState.showUnlinkButton) View.VISIBLE else View.GONE
    }

    private fun getUIState(isConnected: Boolean, isPeerConnected: Boolean): HomeUIState {
        return when {
            isConnected && isPeerConnected -> HomeUIState(
                bgColor = R.color.status_connected_bg,
                textColor = R.color.status_connected_text,
                iconRes = R.drawable.connected,
                textRes = R.string.status_connected,
                showDisconnectedLayout = false,
                showTransferCard = true,
                showUnlinkButton = true
            )
            isConnected -> HomeUIState(
                bgColor = android.R.color.holo_orange_light,
                textColor = android.R.color.holo_orange_dark,
                iconRes = R.drawable.refresh,
                textRes = R.string.status_waiting,
                showDisconnectedLayout = true,
                showTransferCard = false,
                showUnlinkButton = true
            )
            else -> HomeUIState(
                bgColor = R.color.status_disconnected_bg,
                textColor = R.color.status_disconnected_text,
                iconRes = R.drawable.disconnected,
                textRes = R.string.status_not_connected,
                showDisconnectedLayout = true,
                showTransferCard = false,
                showUnlinkButton = false
            )
        }
    }

    private fun unlinkSession() {
        val code = sharedViewModel.sessionCode.value
        if (code != null) {
            repo.deleteSession(code)
            sharedViewModel.clearSession()
            lifecycleScope.launch {
                appPreference.removeSession()
            }
        }
    }

    private fun joinExistingSession() {
        val code = binding.etSessionCode.text.toString().trim()
        if (code.length != 6) {
            binding.etSessionCode.error = getString(R.string.invalid_code)
            return
        }

        repo.joinSession(code, deviceId) { success, error ->
            if (success) {
                lifecycleScope.launch {
                    appPreference.saveSessionCode(code)
                    appPreference.setIsHost(false)
                    sharedViewModel.setSession(code, false)
                    sharedViewModel.setConnected(true)
                }
                binding.etSessionCode.setText("")
                requireContext().showToast(getString(R.string.joined_successfully))
            } else {
                showErrorDialog(error ?: getString(R.string.unknown_error))
            }
        }
    }

    private fun showErrorDialog(msg: String) {
        CustomBottomSheetDialog.newInstance(
            title = getString(R.string.error),
            message = msg,
            showOkButton = false,
            showCancelButton = true
        ).show(childFragmentManager, "error")
    }

    private fun loadPersistedSession() {
        lifecycleScope.launch {
            val code = appPreference.getSessionCode()
            val isHost = appPreference.isHost()
            
            if (code != null) {
                sharedViewModel.setSession(code, isHost)
                sharedViewModel.setConnected(true)
            } else {
                generateNewSession()
            }
        }
    }

    private fun generateNewSession() {
        repo.createSession(deviceId) { code ->
            if (code != null) {
                lifecycleScope.launch {
                    appPreference.saveSessionCode(code)
                    appPreference.setIsHost(true)
                    sharedViewModel.setSession(code, true)
                    sharedViewModel.setConnected(true)
                }
            } else {
                requireContext().showToast("Failed to generate code")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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
