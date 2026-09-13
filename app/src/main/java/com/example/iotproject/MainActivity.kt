package com.example.iotproject

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.iotproject.ui.screens.MainAppScaffold
import com.example.iotproject.ui.theme.IOTProjectTheme
import com.example.iotproject.ui.viewmodel.MainViewModel

import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid_pref", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = "IOTProject/1.0 (Android; Resilient Positioning System)"
        enableEdgeToEdge()
        setContent {
            IOTProjectTheme {
                val viewModel: MainViewModel = viewModel()
                MainAppScaffold(viewModel = viewModel)
            }
        }
    }
}