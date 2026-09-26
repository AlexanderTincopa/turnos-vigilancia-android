package com.alexandertm.turnosvigilancia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alexandertm.turnosvigilancia.ui.TurnosApp
import com.alexandertm.turnosvigilancia.ui.TurnosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TurnosTheme {
                val vm: AppViewModel = viewModel()
                TurnosApp(vm)
            }
        }
    }
}
