package com.testproject.activity

import android.content.Intent
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()

        clipboardMonitor = ClipboardMonitor(this) { text ->
            sharedViewModel.updateText(text)
        }
        clipboardMonitor.start()


        firebaseSyncManager = FirebaseSyncManager(
            context = this,
            clipboardMonitor = clipboardMonitor,
            viewModel = sharedViewModel,
            repo = FirebaseRepository()
        )
        firebaseSyncManager.bind(this)

        handleIncomingSharedText(intent)
        observeViewModel()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingSharedText(intent)
    }

    private fun handleIncomingSharedText(intent: Intent?) {
        intent?.getStringExtra("newSharedText")?.let { text ->
            sharedViewModel.updateText(text)
        }
    }

    private fun observeViewModel() {
        sharedViewModel.lastSentText.observe(this) {
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
        val host = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment

        binding.bottomNav.setupWithNavController(host.navController)
    }
}

