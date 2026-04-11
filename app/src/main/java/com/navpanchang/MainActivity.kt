package com.navpanchang

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.navpanchang.ui.NavPanchangNavGraph
import com.navpanchang.ui.components.NavPanchangBottomBar
import com.navpanchang.ui.theme.NavPanchangTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Hosts the three-tab Compose shell (Home / Calendar / Settings).
 *
 * See TECH_DESIGN.md §UI screens.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NavPanchangTheme {
                NavPanchangScaffold()
            }
        }
    }
}

@Composable
private fun NavPanchangScaffold() {
    val navController = rememberNavController()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = { NavPanchangBottomBar(navController = navController) }
    ) { innerPadding ->
        NavPanchangNavGraph(
            navController = navController,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}
