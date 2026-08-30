package site.addzero.studio.workbench.api

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.koin.compose.koinInject

private enum class ApiInspectorSection { RESPONSE, CODE, HISTORY }
private enum class CompactApiPane { REQUEST, INSPECTOR }

@Composable
fun ApiWorkspace(state: ApiWorkspaceState = koinInject()) {
    val scope = rememberCoroutineScope()
    var inspector by remember { mutableStateOf(ApiInspectorSection.RESPONSE) }
    var authOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { state.load() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = state::updateBaseUrl,
                modifier = Modifier.weight(1f),
                label = { Text("Base URL") },
                singleLine = true,
            )
            if (!state.documentationOnly) {
                IconButton(onClick = { authOpen = true }) {
                    Icon(Icons.Outlined.Key, contentDescription = "鉴权会话")
                }
            }
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 12.dp))
        }
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val execute = {
                scope.launch {
                    state.execute()
                    inspector = ApiInspectorSection.RESPONSE
                }
                Unit
            }
            if (maxWidth < 760.dp) {
                CompactApiWorkspace(
                    state = state,
                    inspector = inspector,
                    onInspectorSelected = { inspector = it },
                    onExecute = execute,
                )
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    OperationTree(state, Modifier.width(280.dp).fillMaxHeight())
                    OperationEditor(state, Modifier.weight(1f).fillMaxHeight(), execute)
                    ApiInspector(
                        state = state,
                        section = inspector,
                        onSelect = { inspector = it },
                        modifier = Modifier.width(360.dp).fillMaxHeight(),
                    )
                }
            }
        }
    }
    if (authOpen) {
        AuthSessionDialog(state, onClose = { authOpen = false })
    }
}

@Composable
private fun CompactApiWorkspace(
    state: ApiWorkspaceState,
    inspector: ApiInspectorSection,
    onInspectorSelected: (ApiInspectorSection) -> Unit,
    onExecute: () -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var pane by remember { mutableStateOf(CompactApiPane.REQUEST) }
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(340.dp)) {
                OperationTree(
                    state = state,
                    modifier = Modifier.fillMaxSize(),
                    onSelected = { scope.launch { drawerState.close() } },
                )
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                    Icon(Icons.Outlined.Menu, contentDescription = "打开 API 目录")
                }
                CompactApiPane.entries.forEach { item ->
                    FilterChip(
                        selected = pane == item,
                        onClick = { pane = item },
                        label = { Text(if (item == CompactApiPane.REQUEST) "请求" else "检查器") },
                    )
                }
            }
            when (pane) {
                CompactApiPane.REQUEST -> OperationEditor(state, Modifier.fillMaxSize(), onExecute)
                CompactApiPane.INSPECTOR -> ApiInspector(
                    state = state,
                    section = inspector,
                    onSelect = onInspectorSelected,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun OperationTree(
    state: ApiWorkspaceState,
    modifier: Modifier,
    onSelected: () -> Unit = {},
) {
    Column(modifier = modifier.padding(8.dp)) {
        OutlinedTextField(
            value = state.filter,
            onValueChange = state::updateFilter,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("搜索接口") },
            singleLine = true,
        )
        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            state.filteredGroups.forEach { group ->
                item("group:${group.name}") {
                    Text(
                        group.name,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                items(group.operations, key = { it.id + it.path }) { operation ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            state.selectOperation(operation)
                            onSelected()
                        },
                        color = if (state.selectedOperation == operation) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(operation.method.uppercase(), color = methodColor(operation.method), fontWeight = FontWeight.Bold)
                            Column {
                                Text(operation.summary, maxLines = 1)
                                Text(operation.path, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OperationEditor(state: ApiWorkspaceState, modifier: Modifier, onExecute: () -> Unit) {
    val operation = state.selectedOperation
    val scope = rememberCoroutineScope()
    Surface(
        modifier = modifier.padding(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.small,
    ) {
        if (operation == null) {
            Text("选择一个接口", modifier = Modifier.padding(16.dp))
            return@Surface
        }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(operation.summary, style = MaterialTheme.typography.titleLarge)
                    Text("${operation.method.uppercase()} ${operation.path}", style = MaterialTheme.typography.bodySmall)
                }
                if (!state.documentationOnly) {
                    Button(onClick = onExecute, enabled = !state.loading) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                        Text("发送")
                    }
                }
            }
            operation.description?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            ParameterSection("路径参数", state.pathValues, state::updatePath)
            ParameterSection("查询参数", state.queryValues, state::updateQuery)
            ParameterSection("请求头", state.headerValues, state::updateHeader)
            if (state.multipartValues.isNotEmpty()) {
                Text("Multipart", style = MaterialTheme.typography.titleSmall)
                multipartFields(operation).forEach { field ->
                    val binary = field.schema["format"]?.jsonPrimitive?.contentOrNull == "binary"
                    if (binary) {
                        Button(onClick = { scope.launch { state.chooseMultipartFile(field.name) } }) {
                            Icon(Icons.Outlined.AttachFile, contentDescription = null)
                            Text(state.multipartFiles[field.name]?.name ?: field.name)
                        }
                    } else {
                        OutlinedTextField(
                            value = state.multipartValues[field.name].orEmpty(),
                            onValueChange = { state.updateMultipart(field.name, it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(field.name) },
                            singleLine = true,
                        )
                    }
                }
            } else if (requestContentType(operation) != null) {
                OutlinedTextField(
                    value = state.bodyText,
                    onValueChange = state::updateBody,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(requestContentType(operation).orEmpty()) },
                    minLines = 10,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
        }
    }
}

@Composable
private fun ParameterSection(
    title: String,
    values: Map<String, String>,
    onChange: (String, String) -> Unit,
) {
    if (values.isEmpty()) return
    Text(title, style = MaterialTheme.typography.titleSmall)
    values.forEach { (name, value) ->
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(name, it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(name) },
            singleLine = true,
        )
    }
}

@Composable
private fun ApiInspector(
    state: ApiWorkspaceState,
    section: ApiInspectorSection,
    onSelect: (ApiInspectorSection) -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier.padding(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            ApiInspectorSection.entries.forEach { item ->
                FilterChip(selected = section == item, onClick = { onSelect(item) }, label = { Text(item.label) })
            }
        }
        when (section) {
            ApiInspectorSection.RESPONSE -> ResponsePanel(state)
            ApiInspectorSection.CODE -> CodePanel(state)
            ApiInspectorSection.HISTORY -> HistoryPanel(state)
        }
    }
}

@Composable
private fun ResponsePanel(state: ApiWorkspaceState) {
    val response = state.response ?: run {
        Text("暂无响应", modifier = Modifier.padding(top = 12.dp))
        return
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("HTTP ${response.status} ${response.statusText}", fontWeight = FontWeight.Bold)
            Text("${response.durationMillis} ms")
        }
        if (response.bytes != null) {
            Button(onClick = state::downloadResponse) {
                Icon(Icons.Outlined.Download, contentDescription = null)
                Text(response.fileName ?: "下载")
            }
        } else {
            Text(response.bodyText.orEmpty(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
        Text("响应头", style = MaterialTheme.typography.titleSmall)
        response.headers.forEach { (name, value) -> Text("$name: $value", style = MaterialTheme.typography.labelSmall) }
        Text("curl", style = MaterialTheme.typography.titleSmall)
        Text(response.curl, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun CodePanel(state: ApiWorkspaceState) {
    Column(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            TypeScriptClient.entries.forEach { client ->
                FilterChip(selected = state.codeClient == client, onClick = { state.selectCodeClient(client) }, label = { Text(client.name) })
            }
        }
        Text(
            state.typeScriptSample,
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun HistoryPanel(state: ApiWorkspaceState) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(state.history, key = { it.id }) { entry ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                Text("${entry.method.uppercase()} · ${entry.status}", fontWeight = FontWeight.Medium)
                Text(entry.url, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                Text("${entry.durationMillis} ms", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun methodColor(method: String) = when (method) {
    "get" -> androidx.compose.ui.graphics.Color(0xFF087F5B)
    "post" -> androidx.compose.ui.graphics.Color(0xFF1C5D99)
    "put", "patch" -> androidx.compose.ui.graphics.Color(0xFF9A6700)
    "delete" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurface
}

private val ApiInspectorSection.label: String
    get() = when (this) {
        ApiInspectorSection.RESPONSE -> "响应"
        ApiInspectorSection.CODE -> "TypeScript"
        ApiInspectorSection.HISTORY -> "历史"
    }
