package com.charles.cruiseapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import com.charles.cruiseapp.ui.screens.PartyScreen
import com.charles.cruiseapp.ui.screens.PartyViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.charles.cruiseapp.ui.theme.CruiseTheme

class PartyTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CruiseTheme {
                Surface(Modifier.fillMaxSize()) {
                    val vm: PartyViewModel = viewModel()
                    // Ensure self name is set for QR
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        if (vm.getSelfName().isBlank()) {
                            vm.setSelfName("TestUser")
                        }
                    }
                    PartyScreen(partyVm = vm, onBack = { finish() })
                }
            }
        }
    }
}
