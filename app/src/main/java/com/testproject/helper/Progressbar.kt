package com.testproject.helper

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.testproject.R
import com.testproject.databinding.LoadingProgressBinding

object Progressbar {

    fun builder(context: Context): Dialog {
        val binding = binding(context)

        return Dialog(context).apply {
            setCanceledOnTouchOutside(false)
            window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            binding.clProgress.visibility = View.VISIBLE
            binding.root.isClickable = false
            binding.root.isFocusable = true
            binding.clProgress.setBackgroundResource(R.color.transparent)
            window?.setBackgroundDrawableResource(R.color.transparent)
            setContentView(binding.root)
        }
    }

    private fun binding(context: Context): LoadingProgressBinding =
        LoadingProgressBinding.inflate(LayoutInflater.from(context))
}