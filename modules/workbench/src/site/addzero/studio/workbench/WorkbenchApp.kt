package site.addzero.studio.workbench

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Api
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import site.addzero.studio.contract.StudioWorkspace
import site.addzero.studio.workbench.agent.AgentWorkspace
import site.addzero.studio.workbench.api.ApiWorkspace
import site.addzero.studio.workbench.library.LibraryWorkspace

@Composable
fun WorkbenchApp() {
    val state = koinInject<WorkbenchState>()
    LaunchedEffect(Unit) { state.initialize() }
    val colors = if (state.darkTheme) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colors) {
        Surface(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val compact = maxWidth < 760.dp
                if (compact) CompactWorkbench(state) else DesktopWorkbench(state)
            }
        }
    }
}

@Composable
private fun DesktopWorkbench(state: WorkbenchState) {
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)),
    ) {
        StudioHeader(state, compact = false)
        HorizontalDivider()
        Row(modifier = Modifier.fillMaxSize()) {
            WorkspaceNavigation(state, Modifier.width(176.dp))
            WorkspaceContent(state, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CompactWorkbench(state: WorkbenchState) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(state.displayName, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                WorkspaceNavigation(
                    state = state,
                    modifier = Modifier.width(250.dp),
                    onSelected = { scope.launch { drawerState.close() } },
                )
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            StudioHeader(state, compact = true, onMenu = { scope.launch { drawerState.open() } })
            HorizontalDivider()
            WorkspaceContent(state, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun StudioHeader(
    state: WorkbenchState,
    compact: Boolean,
    onMenu: () -> Unit = {},
) {
    var tokenOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (compact) {
                IconButton(onClick = onMenu) { Icon(Icons.Outlined.Menu, contentDescription = "打开导航") }
            }
            Column {
                Text(state.displayName, style = MaterialTheme.typography.titleMedium)
                Text(workspaceLabel(state.workspace, state.language), style = MaterialTheme.typography.labelSmall)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { tokenOpen = true }) { Icon(Icons.Outlined.Key, contentDescription = "Bearer Token") }
            IconButton(onClick = state::toggleLanguage) { Icon(Icons.Outlined.Language, contentDescription = "切换语言") }
            IconButton(onClick = state::toggleTheme) {
                Icon(
                    if (state.darkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                    contentDescription = if (state.darkTheme) "使用浅色主题" else "使用深色主题",
                )
            }
        }
    }
    if (tokenOpen) {
        TokenDialog(state, onClose = { tokenOpen = false })
    }
}

@Composable
private fun WorkspaceNavigation(
    state: WorkbenchState,
    modifier: Modifier,
    onSelected: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxSize().padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        workspaceItems.filter { it.workspace in state.availableWorkspaces }.forEach { item ->
            NavigationDrawerItem(
                label = { Text(item.label(state.language)) },
                selected = state.workspace == item.workspace,
                onClick = { state.select(item.workspace); onSelected() },
                icon = { Icon(item.icon, contentDescription = null) },
            )
        }
    }
}

@Composable
private fun WorkspaceContent(state: WorkbenchState, modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            state.error != null -> Text(
                state.error.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.Center).padding(20.dp),
            )
            state.workspace == StudioWorkspace.LIBRARY -> LibraryWorkspace()
            state.workspace == StudioWorkspace.AGENT -> AgentWorkspace()
            state.workspace == StudioWorkspace.API -> ApiWorkspace()
        }
    }
}

@Composable
private fun TokenDialog(state: WorkbenchState, onClose: () -> Unit) {
    var value by remember(state.accessToken) { mutableStateOf(state.accessToken) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Bearer Token") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        },
        confirmButton = {
            Button(onClick = { state.updateAccessToken(value); onClose() }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("取消") } },
    )
}

private data class WorkspaceItem(
    val workspace: StudioWorkspace,
    val zhLabel: String,
    val enLabel: String,
    val icon: ImageVector,
) {
    fun label(language: StudioLanguage) = if (language == StudioLanguage.ZH_CN) zhLabel else enLabel
}

private val workspaceItems = listOf(
    WorkspaceItem(StudioWorkspace.LIBRARY, "Library", "Library", Icons.Outlined.Source),
    WorkspaceItem(StudioWorkspace.AGENT, "Agent", "Agent", Icons.Outlined.Psychology),
    WorkspaceItem(StudioWorkspace.API, "API", "API", Icons.Outlined.Api),
)

private fun workspaceLabel(workspace: StudioWorkspace, language: StudioLanguage): String = when (workspace) {
    StudioWorkspace.LIBRARY -> if (language == StudioLanguage.ZH_CN) "元数据与约定文件" else "Metadata and convention files"
    StudioWorkspace.AGENT -> if (language == StudioLanguage.ZH_CN) "Agent 定义与会话" else "Agent definitions and conversations"
    StudioWorkspace.API -> if (language == StudioLanguage.ZH_CN) "OpenAPI 文档与请求" else "OpenAPI documentation and requests"
}
