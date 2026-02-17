package it.edgvoip.jarvis.ui.screens

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import it.edgvoip.jarvis.sip.SipManager
import javax.inject.Inject

@HiltViewModel
class InCallViewModel @Inject constructor(
    val sipManager: SipManager
) : ViewModel()
