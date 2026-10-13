package com.eonmirth.smartcapture

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier.modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.eonmirth.smartcapture.ui.home.HomeScreen
import com.eonmirth.smartcapture.ui.library.LibraryScreen
import com.eonmirth.smartcapture.ui.settings.SettingsScreen
import com.eonmirth.smartcapture.ui.theme.SmartCaptureTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartCaptureTheme {
                SmartCaptureAppScaffold()
            }
        }
    }
}

private sealed class Dest(val route: String, val labelRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    data object Record : Dest("record", R.string.nav_record, Icons.Default.Videocam)
    data object Library : Dest("library", R.string.nav_library, Icons.Default.List)
    data object Settings : Dest("settings", R.string.nav_settings, Icons.Default.Settings)
}

@Composable
fun SmartCaptureAppScaffold() {
    val navController = rememberNavController()
    val items = listOf(Dest.Record, Dest.Library, Dest.Settings)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                items.forEach { dest ->
                    val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                    NavigationBarItem(
                        icon = { Icon(dest.icon, contentDescription = stringResource(dest.labelRes)) },
                        label = { Text(stringResource(dest.labelRes)) },
                        selected = selected,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Dest.Record.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Dest.Record.route) { HomeScreen() }
            composable(Dest.Library.route) { LibraryScreen() }
            composable(Dest.Settings.route) { SettingsScreen() }
        }
    }
}
