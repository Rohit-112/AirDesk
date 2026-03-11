package com.testproject.activity

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.testproject.base.BaseActivity
import com.testproject.databinding.ActivitySplashBinding
import com.testproject.utils.AppPreference
import com.testproject.utils.openActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
@SuppressLint("CustomSplashScreen")
class SplashActivity : BaseActivity() {

    @Inject
    lateinit var appPreference: AppPreference
    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            delay(2000)
            // Redirecting to MainActivity for now as LoginActivity is not implemented
            openActivity<MainActivity> { }
            finish()
        }
    }
}
