package com.testproject.activity

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.testproject.R
import com.testproject.base.BaseActivity
import com.testproject.data.FirebaseRepository
import com.testproject.data.local.HistoryEntity
import com.testproject.data.local.HistoryRepository
import com.testproject.databinding.ActivityMainBinding
import com.testproject.sync.ClipboardMonitor
import com.testproject.sync.FirebaseSyncManager
import com.testproject.utils.EncryptionHelper
import com.testproject.utils.show
import com.testproject.utils.showToast
import com.testproject.viewmodel.SharedViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private val sharedViewModel: SharedViewModel by viewModels()

    private lateinit var clipboardMonitor: ClipboardMonitor
    private lateinit var firebaseSyncManager: FirebaseSyncManager
    
    @Inject lateinit var historyRepository: HistoryRepository

    private var backPressedTime: Long = 0
    private val TAG = "MainActivityLogs"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()

        // 1. Initialize Monitor
        clipboardMonitor = ClipboardMonitor(this) { text ->
            Log.d(TAG, "Local copy detected: $text")
            sharedViewModel.updateText(text)
        }
        clipboardMonitor.start()

        // 2. Initialize Sync Manager
        val encryptionHelper = EncryptionHelper(this)
        firebaseSyncManager = FirebaseSyncManager(
            context = this,
            clipboardMonitor = clipboardMonitor,
            viewModel = sharedViewModel,
            encryptionHelper = encryptionHelper,
            repo = FirebaseRepository()
        )
        firebaseSyncManager.bind(this)

        // 3. Handle data shared from other apps
        handleIncomingIntent(intent)
        observeViewModel()
        setupBackPressed()
    }

    private fun setupBackPressed() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (navController.currentDestination?.id == R.id.homeFragment) {
                    if (backPressedTime + 2000 > System.currentTimeMillis()) {
                        finishAffinity() // Exit app and clear stack
                    } else {
                        showToast("Press back again to exit")
                    }
                    backPressedTime = System.currentTimeMillis()
                } else {
                    navController.popBackStack()
                }
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return

        val type = intent.type ?: return
        
        lifecycleScope.launch {
            if (type == "text/plain") {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
                    saveToQueue(text, isFile = false)
                }
            } else {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                
                uri?.let {
                    saveToQueue(it.toString(), isFile = true, fileName = getFileName(it))
                }
            }
        }
    }

    private suspend fun saveToQueue(content: String, isFile: Boolean, fileName: String? = null) {
        historyRepository.insertHistory(
            HistoryEntity(
                content = content,
                isReceived = false,
                isFile = isFile,
                fileName = fileName,
                isQueued = true
            )
        )
        showToast("Added to Queue")
    }

    private fun getFileName(uri: Uri): String {
        var name = "file_${System.currentTimeMillis()}"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) name = cursor.getString(index)
            }
        }
        return name
    }

    private fun observeViewModel() {
        sharedViewModel.connected.observe(this) { isConnected ->
            Log.d(TAG, "Connection status: $isConnected")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clipboardMonitor.stop()
        firebaseSyncManager.shutdown()
    }

    fun btmNavShow(isShown: Boolean = true) {
        binding.bottomNav.show(isShown)
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNav.setupWithNavController(navController)
    }
}
