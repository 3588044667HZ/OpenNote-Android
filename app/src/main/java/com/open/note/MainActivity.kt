package com.open.note

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.open.note.data.skin.SkinColors
import com.open.note.data.skin.SkinManager
import com.open.note.ui.editor.NoteEditorActivity
import com.open.note.ui.login.LoginActivity
import com.open.note.ui.notes.NoteListScreen
import com.open.note.ui.settings.SettingsScreen
import com.open.note.ui.skin.SkinViewModel
import com.open.note.ui.trash.TrashScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var skinManager: SkinManager

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        skinManager.refreshSkin()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainScreen(
                    onOpenEditor = { noteId ->
                        startActivity(NoteEditorActivity.newIntent(this, noteId))
                    },
                    onLogout = {
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}

sealed class BottomTab(val route: String, val title: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    data object Notes : BottomTab("notes", "Notes", Icons.Filled.Note, Icons.Outlined.Note)
    data object Trash : BottomTab("trash", "Trash", Icons.Filled.Delete, Icons.Outlined.Delete)
    data object Settings : BottomTab("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onOpenEditor: (String?) -> Unit,
    onLogout: () -> Unit,
    skinViewModel: SkinViewModel = hiltViewModel()
) {
    val tabs = listOf(BottomTab.Notes, BottomTab.Trash, BottomTab.Settings)
    var selectedTab by remember { mutableStateOf<BottomTab>(BottomTab.Notes) }
    val skin by skinViewModel.selectedSkin.collectAsState()
    val skinBg = SkinColors.parseColor(skin.backCloth)

    Scaffold(
        containerColor = skinBg,
        topBar = {
            TopAppBar(
                title = { Text(when (selectedTab) {
                    BottomTab.Notes -> "Notes"
                    BottomTab.Trash -> "Trash"
                    BottomTab.Settings -> "Settings"
                }) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = skinBg)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = skinBg) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        icon = { Icon(if (selectedTab == tab) tab.selectedIcon else tab.unselectedIcon, contentDescription = tab.title) },
                        label = { Text(tab.title) },
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab }
                    )
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == BottomTab.Notes) {
                FloatingActionButton(onClick = { onOpenEditor(null) }) {
                    Icon(Icons.Default.Add, contentDescription = "New Note")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            when (selectedTab) {
                BottomTab.Notes -> NoteListScreen(
                    onNoteClick = { noteId -> onOpenEditor(noteId) },
                    onNewNote = { onOpenEditor(null) }
                )
                BottomTab.Trash -> TrashScreen()
                BottomTab.Settings -> SettingsScreen(onLogout = onLogout)
            }
        }
    }
}
