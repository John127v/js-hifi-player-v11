package com.jshifi.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            JSHiFiPlayerApp()
        }
    }
}

@Composable
private fun JSHiFiPlayerApp() {

    MaterialTheme {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF06070A)),
            contentAlignment = Alignment.Center
        ) {

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                Text(
                    text = "JS HIFI PLAYER",
                    color = Color(0xFF00E5FF),
                    fontSize = 24.sp
                )

                Text(
                    text = "Inicializando...",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
        }
    }
}
