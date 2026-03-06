package com.testproject.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.testproject.R
import com.testproject.base.BaseActivity
import com.testproject.data.FirebaseRepository
import com.testproject.databinding.ActivityMainBinding
import com.testproject.sync.ClipboardMonitor
import com.testproject.sync.FirebaseSyncManager
import com.testproject.utils.show
import com.testproject.viewmodel.SharedViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private val sharedViewModel: SharedViewModel by viewModels()

    private lateinit var clipboardMonitor: ClipboardMonitor
    private lateinit var firebaseSyncManager: FirebaseSyncManager

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
        firebaseSyncManager = FirebaseSyncManager(
            context = this,
            clipboardMonitor = clipboardMonitor,
            viewModel = sharedViewModel,
            repo = FirebaseRepository()
        )
        firebaseSyncManager.bind(this)

        // 3. Handle data shared from other apps
        handleIncomingSharedText(intent)
        observeViewModel()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingSharedText(intent)
    }

    private fun handleIncomingSharedText(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && "text/plain" == intent.type) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrEmpty()) {
                Log.d(TAG, "Incoming shared text: $sharedText")
                sharedViewModel.updateText(sharedText)
                // Also update the system clipboard so it's ready to be sent
                clipboardMonitor.setClipboardProgrammatically(sharedText)
            }
        }
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
