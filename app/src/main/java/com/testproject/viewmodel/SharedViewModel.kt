package com.testproject.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SharedViewModel : ViewModel() {
    private val _lastSentText = MutableLiveData<String>()
    val lastSentText: LiveData<String> get() = _lastSentText

    fun updateText(text: String) {
        _lastSentText.postValue(text)
    }
}
