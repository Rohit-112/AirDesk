package com.testproject.fragment

import android.annotation.SuppressLint
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.testproject.R
import com.testproject.adapter.HistoryAdapter
import com.testproject.adapter.QueueAdapter
import com.testproject.base.BaseFragment
import com.testproject.databinding.FragmentHomeBinding
import com.testproject.domain.model.HistoryItem
import com.testproject.helper.CustomBottomSheetDialog
import com.testproject.network.NetworkUtils
import com.testproject.utils.AppsConst.FILE_PROTOCOL_PREFIX
import com.testproject.utils.AppsConst.FILE_PROTOCOL_SEPARATOR
import com.testproject.utils.showToast
import com.testproject.viewmodel.HomeViewModel
import com.testproject.viewmodel.SharedViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import androidx.core.net.toUri

@AndroidEntryPoint
class HomeFragment : BaseFragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    
    private val sharedViewModel: SharedViewModel by activityViewModels()
    private val viewModel: HomeViewModel by viewModels()

    private lateinit var sharedAdapter: HistoryAdapter
    private lateinit var receivedAdapter: HistoryAdapter
    private lateinit var queueAdapter: QueueAdapter

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handleFileSelection(it) }
    }

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
        observeLocalHistory()
        loadPersistedSession()
    }

    private fun setupRecyclerViews() {
        sharedAdapter = HistoryAdapter()
        receivedAdapter = HistoryAdapter()
        queueAdapter = QueueAdapter { item -> handleQueueItemClick(item) }

        binding.rvSharedItems.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = sharedAdapter
        }

        binding.rvReceivedItems.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = receivedAdapter
        }

        binding.rvQueueItems.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = queueAdapter
        }
    }

    private fun observeLocalHistory() {
        viewModel.sharedHistory.observe(viewLifecycleOwner) { items ->
            sharedAdapter.updateItems(items)
        }
        viewModel.receivedHistory.observe(viewLifecycleOwner) { items ->
            receivedAdapter.updateItems(items)
        }
        viewModel.queuedHistory.observe(viewLifecycleOwner) { items ->
            queueAdapter.updateItems(items)
            binding.queueCard.visibility = if (items.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun handleQueueItemClick(item: HistoryItem) {
        if (sharedViewModel.connected.value == true && sharedViewModel.peerConnected.value == true) {
            shareQueueItem(item)
        } else {
            val msg = if (sharedViewModel.connected.value != true) {
                "Please connect to a session first to share this item."
            } else {
                "Waiting for a peer to connect to your session..."
            }
            
            CustomBottomSheetDialog.newInstance(
                title = "Not Ready to Share",
                message = msg,
                okText = "Got it",
                showCancelButton = false
            ).show(childFragmentManager, "not_connected")
        }
    }

    private fun shareQueueItem(item: HistoryItem) {
        lifecycleScope.launch {
            if (item.isFile) {
                val uri = item.content.toUri()
                if (!viewModel.isFileSizeValid(requireContext(), uri)) {
                    showFileSizeError()
                    return@launch
                }
                
                val sessionCode = sharedViewModel.sessionCode.value ?: return@launch
                showLoading()
                val downloadUrl = viewModel.uploadFile(sessionCode, uri, item.fileName ?: "file")
                hideLoading()
                
                if (downloadUrl != null) {
                    val protocolString = "$FILE_PROTOCOL_PREFIX${item.fileName}$FILE_PROTOCOL_SEPARATOR$downloadUrl"
                    sharedViewModel.updateText(protocolString)
                    viewModel.markAsNotQueued(item.id)
                    requireContext().showToast("File shared successfully!")
                } else {
                    requireContext().showToast("Failed to upload file")
                }
            } else {
                sharedViewModel.updateText(item.content)
                viewModel.markAsNotQueued(item.id)
                requireContext().showToast("Text shared!")
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnConnect.setOnClickListener { if (checkNetwork()) joinExistingSession() }
        binding.btnRefreshCode.setOnClickListener {
            if (checkNetwork()) {
                sharedViewModel.sessionCode.value?.let { viewModel.deleteSession(it) }
                generateNewSession()
            }
        }
        binding.btnUnlink.setOnClickListener { unlinkSession() }
        binding.btnShareText.setOnClickListener { shareCustomText() }
        binding.btnSelectFiles.setOnClickListener { if (checkNetwork()) filePickerLauncher.launch("*/*") }
    }

    private fun handleFileSelection(uri: Uri) {
        if (!viewModel.isFileSizeValid(requireContext(), uri)) {
            showFileSizeError()
            return
        }
        
        val fileName = getFileName(uri)
        val sessionCode = sharedViewModel.sessionCode.value ?: return

        showLoading()
        lifecycleScope.launch {
            val downloadUrl = viewModel.uploadFile(sessionCode, uri, fileName)
            hideLoading()
            if (downloadUrl != null) {
                val protocolString = "$FILE_PROTOCOL_PREFIX$fileName$FILE_PROTOCOL_SEPARATOR$downloadUrl"
                saveToLocalHistory(content = protocolString, isReceived = false, isFile = true, fileName = fileName)
                sharedViewModel.updateText(protocolString)
                requireContext().showToast("File shared successfully!")
            } else {
                requireContext().showToast("Failed to upload file")
            }
        }
    }

    private fun shareCustomText() {
        val text = binding.etShareText.text.toString().trim()
        if (text.isNotEmpty()) {
            saveToLocalHistory(content = text, isReceived = false, isFile = false)
            sharedViewModel.updateText(text)
            binding.etShareText.setText("")
            requireContext().showToast("Text shared!")
        } else {
            requireContext().showToast("Please enter some text")
        }
    }

    private fun handleReceivedContent(content: String) {
        if (content.startsWith(FILE_PROTOCOL_PREFIX)) {
            val fileData = content.removePrefix(FILE_PROTOCOL_PREFIX).split(FILE_PROTOCOL_SEPARATOR)
            if (fileData.size == 2) {
                val fileName = fileData[0]
                saveToLocalHistory(content = content, isReceived = true, isFile = true, fileName = fileName)
                showDownloadDialog(fileName, fileData[1])
            }
        } else {
            saveToLocalHistory(content = content, isReceived = true, isFile = false)
            requireContext().showToast("Text updated to clipboard")
        }
    }

    private fun saveToLocalHistory(content: String, isReceived: Boolean, isFile: Boolean, fileName: String? = null) {
        viewModel.saveToHistory(
            HistoryItem(content = content, isReceived = isReceived, isFile = isFile, fileName = fileName)
        )
    }

    private fun observeViewModel() {
        sharedViewModel.connected.observe(viewLifecycleOwner) { updateStatusUI() }
        sharedViewModel.peerConnected.observe(viewLifecycleOwner) { updateStatusUI() }
        sharedViewModel.sessionCode.observe(viewLifecycleOwner) { code ->
            binding.tvYourCode.text = code ?: "------"
            binding.tvConnectedTo.text = code?.let { getString(R.string.connected_to, it) } ?: getString(R.string.status_not_connected)
        }
        sharedViewModel.receivedContent.observe(viewLifecycleOwner) { content ->
            content?.let {
                handleReceivedContent(it)
                sharedViewModel.setReceivedContent(null)
            }
        }
    }

    private fun saveFileToGallery(fileName: String, downloadUrl: String) {
        showLoading()
        lifecycleScope.launch {
            val bytes = viewModel.downloadFileBytes(downloadUrl)
            if (bytes != null) {
                if (saveFileToPublicDirectory(fileName, bytes)) {
                    viewModel.deleteFileByUrl(downloadUrl)
                    requireContext().showToast("File saved to Downloads")
                } else {
                    requireContext().showToast("Failed to save file locally")
                }
            } else {
                requireContext().showToast("Failed to download file")
            }
            hideLoading()
        }
    }

    private suspend fun saveFileToPublicDirectory(fileName: String, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = requireContext().contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "*/*")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)?.let { uri ->
                    resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    true
                } ?: false
            } else {
                val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
                FileOutputStream(file).use { it.write(bytes) }
                true
            }
        } catch (e: Exception) { false }
    }

    private fun updateStatusUI() {
        val isConnected = sharedViewModel.connected.value ?: false
        val isPeerConnected = sharedViewModel.peerConnected.value ?: false
        val uiState = getUIState(isConnected, isPeerConnected)

        binding.statusCard.setCardBackgroundColor(ContextCompat.getColor(requireContext(), uiState.bgColor))
        binding.ivStatusIcon.setImageResource(uiState.iconRes)
        binding.ivStatusIcon.setColorFilter(ContextCompat.getColor(requireContext(), uiState.textColor))
        binding.tvStatusText.apply {
            text = getString(uiState.textRes)
            setTextColor(ContextCompat.getColor(requireContext(), uiState.textColor))
        }
        binding.tvConnectedTo.setTextColor(ContextCompat.getColor(requireContext(), uiState.textColor))
        binding.layoutDisconnected.visibility = if (uiState.showDisconnectedLayout) View.VISIBLE else View.GONE
        binding.transferCard.visibility = if (uiState.showTransferCard) View.VISIBLE else View.GONE
        binding.btnUnlink.visibility = if (uiState.showUnlinkButton) View.VISIBLE else View.GONE
    }

    private fun getUIState(isConnected: Boolean, isPeerConnected: Boolean) = when {
        isConnected && isPeerConnected -> HomeUIState(R.color.status_connected_bg, R.color.status_connected_text, R.drawable.connected, R.string.status_connected, false, true, true)
        isConnected -> HomeUIState(android.R.color.holo_orange_light, android.R.color.holo_orange_dark, R.drawable.refresh, R.string.status_waiting, true, false, true)
        else -> HomeUIState(R.color.status_disconnected_bg, R.color.status_disconnected_text, R.drawable.disconnected, R.string.status_not_connected, true, false, false)
    }

    private fun unlinkSession() {
        sharedViewModel.sessionCode.value?.let {
            viewModel.deleteSession(it)
            sharedViewModel.clearSession()
            lifecycleScope.launch { viewModel.clearPersistedSession() }
        }
    }

    private fun joinExistingSession() {
        val code = binding.etSessionCode.text.toString().trim()
        if (code.length != 6) { binding.etSessionCode.error = getString(R.string.invalid_code); return }
        viewModel.joinSession(code, deviceId) { success, error ->
            if (success) {
                lifecycleScope.launch {
                    viewModel.saveSession(code, false)
                    sharedViewModel.setSession(code, false)
                    sharedViewModel.setConnected(true)
                }
                binding.etSessionCode.setText("")
            } else showErrorDialog(error ?: "Unknown error")
        }
    }

    private fun loadPersistedSession() = lifecycleScope.launch {
        viewModel.getSessionCode()?.let {
            sharedViewModel.setSession(it, viewModel.isHost())
            sharedViewModel.setConnected(true)
        } ?: generateNewSession()
    }

    private fun generateNewSession() = viewModel.createSession(deviceId) { code ->
        code?.let {
            lifecycleScope.launch {
                viewModel.saveSession(it, true)
                sharedViewModel.setSession(it, true)
                sharedViewModel.setConnected(true)
            }
        } ?: requireContext().showToast("Failed to generate code")
    }

    private fun checkNetwork() = if (NetworkUtils.isConnected(requireContext())) true else { requireContext().showToast(getString(R.string.no_internet)); false }

    @SuppressLint("Range")
    private fun getFileName(uri: Uri): String {
        var name = "file_${System.currentTimeMillis()}"
        requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) name = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
        }
        return name
    }

    private fun showDownloadDialog(fileName: String, url: String) = CustomBottomSheetDialog.newInstance(
        title = "File Received",
        message = "You received: $fileName. Download?",
        okText = "Download",
        cancelText = "Ignore",
        showOkButton = true,
        showCancelButton = true,
        onOkClicked = { saveFileToGallery(fileName, url) }
    ).show(childFragmentManager, "dl")

    private fun showFileSizeError() = CustomBottomSheetDialog.newInstance(
        title = "File Too Large",
        message = "Max size 5MB.",
        showOkButton = false,
        cancelText = "Got it",
        showCancelButton = true
    ).show(childFragmentManager, "error")

    private fun showErrorDialog(msg: String) = CustomBottomSheetDialog.newInstance(
        title = "Error",
        message = msg,
        showOkButton = true,
        showCancelButton = false
    ).show(childFragmentManager, "err")

    override fun onDestroyView() { super.onDestroyView(); _binding = null }

    private data class HomeUIState(val bgColor: Int, val textColor: Int, val iconRes: Int, val textRes: Int, val showDisconnectedLayout: Boolean, val showTransferCard: Boolean, val showUnlinkButton: Boolean)
}
