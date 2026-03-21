package com.testproject.activity

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.testproject.R
import com.testproject.base.BaseActivity
import com.testproject.domain.model.HistoryItem
import com.testproject.domain.usecase.InsertHistoryUseCase
import com.testproject.databinding.ActivityMainBinding
import com.testproject.sync.ClipboardMonitor
import com.testproject.utils.show
import com.testproject.utils.showToast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var clipboardMonitor: ClipboardMonitor
    
    @Inject lateinit var insertHistoryUseCase: InsertHistoryUseCase

    private var backPressedTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()

        clipboardMonitor = ClipboardMonitor(this) { }
        clipboardMonitor.start()

        handleIncomingIntent(intent)
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
                        finishAffinity()
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
        insertHistoryUseCase(
            HistoryItem(
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

    override fun onDestroy() {
        super.onDestroy()
        clipboardMonitor.stop()
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
