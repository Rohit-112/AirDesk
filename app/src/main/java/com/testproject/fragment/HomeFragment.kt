package com.testproject.fragment

import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.testproject.R
import com.testproject.activity.CustomScannerActivity
import com.testproject.adapter.HistoryAdapter
import com.testproject.adapter.QueueAdapter
import com.testproject.base.BaseFragment
import com.testproject.databinding.FragmentHomeBinding
import com.testproject.domain.model.HistoryItem
import com.testproject.domain.webrtc.TransferProgress
import com.testproject.utils.*
import com.testproject.utils.AppsConst.FILE_PROTOCOL_PREFIX
import com.testproject.utils.AppsConst.FILE_PROTOCOL_SEPARATOR
import com.testproject.viewmodel.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : BaseFragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val sessionViewModel: SessionViewModel by viewModels()
    private val historyViewModel: HistoryViewModel by viewModels()
    private val fileTransferViewModel: FileTransferViewModel by viewModels()
    private val webRTCViewModel: WebRTCViewModel by viewModels()

    @Inject lateinit var clipboardHelper: ClipboardHelper
    @Inject lateinit var fileHelper: FileHelper
    @Inject lateinit var qrHelper: QRHelper
    @Inject lateinit var networkHelper: NetworkHelper
    @Inject lateinit var sessionHelper: SessionHelper
    @Inject lateinit var deviceHelper: DeviceHelper

    private lateinit var dialogHelper: DialogHelper
    private lateinit var uiHelper: HomeUIHelper

    private lateinit var sharedAdapter: HistoryAdapter
    private lateinit var receivedAdapter: HistoryAdapter
    private lateinit var queueAdapter: QueueAdapter

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { it?.let { handleFileSelection(it) } }

    private val qrScannerLauncher = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { code ->
            if (code.length == 6 && code.all { it.isDigit() }) {
                binding.layoutDisconnected.etSessionCode.setText(code)
                joinSession(code)
            } else requireContext().showToast("Invalid QR Code")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialogHelper = DialogHelper(childFragmentManager)
        uiHelper = HomeUIHelper(binding)
        btmNavShow(true)
        setupRecyclerViews()
        setupClickListeners()
        observeViewModels()
        observeLocalHistory()
        sessionViewModel.loadPersistedSession()
        uiHelper.animateEntrance()
    }

    private fun setupRecyclerViews() {
        sharedAdapter = HistoryAdapter { handleHistoryItemClick(it) }
        receivedAdapter = HistoryAdapter { handleHistoryItemClick(it) }
        queueAdapter = QueueAdapter { handleQueueItemClick(it) }

        binding.layoutHistory.rvSharedItems.apply { layoutManager = LinearLayoutManager(requireContext()); adapter = sharedAdapter }
        binding.layoutHistory.rvReceivedItems.apply { layoutManager = LinearLayoutManager(requireContext()); adapter = receivedAdapter }
        binding.layoutQueue.rvQueueItems.apply { layoutManager = LinearLayoutManager(requireContext()); adapter = queueAdapter }
    }

    private fun handleHistoryItemClick(item: HistoryItem) {
        if (item.isFile) {
            val fileName = item.fileName ?: "file"
            // Check if file exists locally in Downloads
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
            
            if (file.exists()) {
                FileUtils.openFile(requireContext(), file)
            } else if (item.isReceived) {
                // If it doesn't exist but was received, try to download
                parseFileProtocol(item.content)?.let { (name, url) ->
                    dialogHelper.showDownloadDialog(name, url) { downloadFile(name, url) }
                } ?: requireContext().showToast("File record missing info")
            } else {
                requireContext().showToast("Local file not found")
            }
        } else {
            clipboardHelper.copyToClipboard(item.content)
            requireContext().showToast("Copied to clipboard")
        }
    }

    private fun parseFileProtocol(content: String): Pair<String, String>? {
        if (!content.startsWith(FILE_PROTOCOL_PREFIX)) return null
        val data = content.removePrefix(FILE_PROTOCOL_PREFIX).split(FILE_PROTOCOL_SEPARATOR)
        return if (data.size == 2) data[0] to data[1] else null
    }

    private fun observeLocalHistory() {
        historyViewModel.sharedHistory.observe(viewLifecycleOwner) { shared ->
            historyViewModel.receivedHistory.observe(viewLifecycleOwner) { received ->
                sharedAdapter.updateItems(shared)
                receivedAdapter.updateItems(received)
                uiHelper.updateHistoryVisibility(shared.size, received.size)
            }
        }
        historyViewModel.queuedHistory.observe(viewLifecycleOwner) {
            queueAdapter.updateItems(it)
            uiHelper.updateQueueVisibility(it.isEmpty())
        }
    }

    private fun handleQueueItemClick(item: HistoryItem) {
        if (sessionViewModel.sessionCode.value != null && sessionViewModel.peerOnline.value == true) {
            shareQueueItem(item)
        } else {
            val msg = if (sessionViewModel.sessionCode.value == null) "Connect to a session first." else "Waiting for a peer..."
            dialogHelper.showNotReadyToShareDialog(msg)
        }
    }

    private fun shareQueueItem(item: HistoryItem) = lifecycleScope.launch {
        if (item.isFile) {
            val uri = item.content.toUri()
            if (!fileTransferViewModel.isFileSizeValid(requireContext(), uri)) {
                dialogHelper.showFileSizeError(); return@launch
            }
            if (sessionViewModel.sessionCode.value == null) return@launch
            showLoading()
            val url = fileTransferViewModel.uploadFile(sessionViewModel.sessionCode.value!!, uri, item.fileName ?: "file")
            hideLoading()
            if (url != null) {
                val protocol = "$FILE_PROTOCOL_PREFIX${item.fileName}$FILE_PROTOCOL_SEPARATOR$url"
                sessionViewModel.sendContent(protocol)
                historyViewModel.markAsNotQueued(item.id)
                requireContext().showToast("File shared!")
            } else requireContext().showToast("Upload failed")
        } else {
            sessionViewModel.sendContent(item.content)
            historyViewModel.markAsNotQueued(item.id)
            requireContext().showToast("Text shared!")
        }
    }

    private fun setupClickListeners() {
        binding.layoutDisconnected.btnConnect.setOnClickListener { if (networkHelper.checkNetworkWithToast()) joinExistingSession() }
        binding.layoutDisconnected.btnRefreshCode.setOnClickListener {
            if (networkHelper.checkNetworkWithToast()) {
                sessionHelper.unlinkSession(sessionViewModel)
                sessionHelper.createSession(sessionViewModel)
            }
        }
        binding.layoutDisconnected.btnScanQr.setOnClickListener { startQrScanner() }
        binding.layoutDisconnected.btnShowQr.setOnClickListener { sessionViewModel.sessionCode.value?.let { qrHelper.showQrCodeDialog(requireContext(), it) } }
        binding.layoutStatus.btnUnlink.setOnClickListener { sessionHelper.unlinkSession(sessionViewModel) }
        binding.layoutTransfer.btnShareText.setOnClickListener { shareCustomText() }
        binding.layoutTransfer.btnSelectFiles.setOnClickListener { if (networkHelper.checkNetworkWithToast()) filePickerLauncher.launch("*/*") }
    }

    private fun startQrScanner() = qrScannerLauncher.launch(ScanOptions().apply {
        setCaptureActivity(CustomScannerActivity::class.java); setDesiredBarcodeFormats(ScanOptions.QR_CODE); setPrompt("Align QR code"); setBeepEnabled(false); setOrientationLocked(false)
    })

    private fun handleFileSelection(uri: Uri) {
        val name = fileHelper.getFileName(uri)
        if (sessionViewModel.sessionCode.value == null) {
            requireContext().showToast("Connect to a session first")
            return
        }
        
        lifecycleScope.launch {
            val bytes = requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                webRTCViewModel.sendFile(name, bytes)
            } else {
                requireContext().showToast("Could not read file")
            }
        }
    }

    private fun shareCustomText() {
        val text = binding.layoutTransfer.etShareText.text.toString().trim()
        if (text.isNotEmpty()) {
            historyViewModel.saveToHistory(HistoryItem(content = text, isReceived = false, isFile = false))
            sessionViewModel.sendContent(text)
            binding.layoutTransfer.etShareText.setText("")
            requireContext().showToast("Text shared!")
        } else requireContext().showToast("Enter some text")
    }

    private fun handleReceivedContent(content: String) {
        val protocol = parseFileProtocol(content)
        if (protocol != null) {
            val (name, url) = protocol
            historyViewModel.saveToHistory(HistoryItem(content = content, isReceived = true, isFile = true, fileName = name))
            dialogHelper.showDownloadDialog(name, url) { downloadFile(name, url) }
        } else {
            historyViewModel.saveToHistory(HistoryItem(content = content, isReceived = true, isFile = false))
            clipboardHelper.copyToClipboard(content)
            requireContext().showToast("Clipboard updated")
        }
    }

    private fun observeViewModels() {
        sessionViewModel.sessionCode.observe(viewLifecycleOwner) { code ->
            binding.layoutDisconnected.tvYourCode.text = code ?: "------"
            binding.layoutStatus.tvConnectedTo.text = code?.let { getString(R.string.connected_to, it) } ?: getString(R.string.status_not_connected)
            uiHelper.updateStatusUI(code != null, sessionViewModel.peerOnline.value ?: false)
            
            if (code != null) {
                webRTCViewModel.initConnection(code, sessionViewModel.isHost.value ?: true)
            }
        }
        
        sessionViewModel.isHost.observe(viewLifecycleOwner) { 
            uiHelper.updateStatusUI(sessionViewModel.sessionCode.value != null, sessionViewModel.peerOnline.value ?: false) 
        }
        
        sessionViewModel.peerOnline.observe(viewLifecycleOwner) { 
            uiHelper.updateStatusUI(sessionViewModel.sessionCode.value != null, it ?: false) 
        }
        
        sessionViewModel.receivedContent.observe(viewLifecycleOwner) {
            it?.let {
                handleReceivedContent(it)
                sessionViewModel.consumeReceivedContent()
            }
        }

        webRTCViewModel.transferProgress.observe(viewLifecycleOwner) { progress ->
            when (progress) {
                is TransferProgress.Progress -> { /* UI update logic */ }
                is TransferProgress.Success -> {
                    requireContext().showToast("File sent via WebRTC: ${progress.fileName}")
                    historyViewModel.saveToHistory(HistoryItem(content = "WEBRTC:${progress.fileName}", isReceived = false, isFile = true, fileName = progress.fileName))
                }
                is TransferProgress.Error -> {
                    requireContext().showToast("WebRTC failed: ${progress.message}")
                }
            }
        }

        webRTCViewModel.incomingFile.observe(viewLifecycleOwner) { file ->
            historyViewModel.saveToHistory(HistoryItem(content = "WEBRTC:${file.fileName}", isReceived = true, isFile = true, fileName = file.fileName))
            lifecycleScope.launch {
                val success = fileHelper.saveFileToPublicDirectory(file.fileName, file.fileBytes)
                requireContext().showToast(if (success) "Received via WebRTC: ${file.fileName}" else "Failed to save WebRTC file")
            }
        }
    }

    private fun downloadFile(name: String, url: String) = lifecycleScope.launch {
        fileHelper.downloadAndSaveFile(name, url, fileTransferViewModel, { showLoading() }) { success ->
            hideLoading()
            requireContext().showToast(if (success) "Saved to Downloads" else "Download failed")
        }
    }

    private fun joinExistingSession() {
        val code = binding.layoutDisconnected.etSessionCode.text.toString().trim()
        if (code.length != 6) { binding.layoutDisconnected.etSessionCode.error = getString(R.string.invalid_code); return }
        joinSession(code)
    }

    private fun joinSession(code: String) = sessionHelper.joinSession(code, sessionViewModel,
        onSuccess = { binding.layoutDisconnected.etSessionCode.setText("") },
        onError = { dialogHelper.showErrorDialog(it) }
    )

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
