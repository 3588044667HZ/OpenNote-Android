package com.open.note

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.lifecycle.lifecycleScope
import com.open.note.data.repository.AuthRepository
import com.open.note.data.skin.SkinColors
import com.open.note.data.skin.SkinManager
import com.open.note.ui.editor.NoteEditorActivity
import com.open.note.ui.login.LoginActivity
import com.open.note.ui.notes.NoteListScreen
import com.open.note.ui.settings.SettingsScreen
import com.open.note.ui.skin.SkinViewModel
import com.open.note.ui.trash.TrashScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var skinManager: SkinManager
    @Inject lateinit var authRepository: AuthRepository

    private val isLoggedIn = MutableStateFlow(false)

    private val loginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // 登录页返回后重新检查登录态（登录成功则触发同步）
        lifecycleScope.launch {
            isLoggedIn.value = authRepository.isLoggedIn()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        skinManager.refreshSkin()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            isLoggedIn.value = authRepository.isLoggedIn()
        }
        setContent {
            MaterialTheme {
                val loggedIn by isLoggedIn.collectAsState()
                MainScreen(
                    isLoggedIn = loggedIn,
                    onOpenEditor = { noteId, localId ->
                        startActivity(NoteEditorActivity.newIntent(this, noteId, localId))
                    },
                    onOpenLogin = {
                        loginLauncher.launch(Intent(this, LoginActivity::class.java))
                    },
                    onLogout = {
                        // SettingsViewModel.logout() 已完成 token 清理，这里仅更新 UI 状态
                        isLoggedIn.value = false
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
    isLoggedIn: Boolean,
    onOpenEditor: (String?, Long?) -> Unit,
    onOpenLogin: () -> Unit,
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
                FloatingActionButton(onClick = { onOpenEditor(null, null) }) {
                    Icon(Icons.Default.Add, contentDescription = "New Note")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            when (selectedTab) {
                BottomTab.Notes -> NoteListScreen(
                    isLoggedIn = isLoggedIn,
                    onNoteClick = { noteId, localId -> onOpenEditor(noteId, localId) },
                    onNewNote = { onOpenEditor(null, null) }
                )
                BottomTab.Trash -> TrashScreen()
                BottomTab.Settings -> SettingsScreen(
                    isLoggedIn = isLoggedIn,
                    onLogin = onOpenLogin,
                    onLogout = onLogout
                )
            }
        }
    }
}
