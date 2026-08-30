package site.addzero.studio.workbench.agent

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
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Stop
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.compose.koinInject
import site.addzero.studio.contract.AgentDefinitionCommand
import site.addzero.studio.contract.MetadataPatchCommand

private enum class CompactAgentPane { CHAT, EVENTS }

@Composable
fun AgentWorkspace(state: AgentWorkspaceState = koinInject()) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { state.load() }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AgentSection.entries.forEach { section ->
                FilterChip(
                    selected = state.section == section,
                    onClick = { state.selectSection(section) },
                    label = { Text(section.label) },
                )
            }
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
        }
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val compact = maxWidth < 760.dp
            when (state.section) {
                AgentSection.DEFINITIONS -> DefinitionWorkspace(state, compact)
                AgentSection.CHAT -> ChatWorkspace(state, compact)
                AgentSection.SETTINGS -> SettingsWorkspace(state)
            }
        }
    }
}

@Composable
private fun DefinitionWorkspace(state: AgentWorkspaceState, compact: Boolean) {
    val scope = rememberCoroutineScope()
    if (compact) {
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(modifier = Modifier.width(320.dp)) {
                    DefinitionList(state, Modifier.fillMaxSize()) {
                        scope.launch { drawerState.close() }
                    }
                }
            },
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                    Icon(Icons.Outlined.Menu, contentDescription = "打开 Agent 目录")
                }
                DefinitionContent(state, Modifier.weight(1f))
            }
        }
    } else {
        Row(modifier = Modifier.fillMaxSize()) {
            DefinitionList(state, Modifier.width(240.dp).fillMaxHeight())
            DefinitionContent(state, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun DefinitionList(
    state: AgentWorkspaceState,
    modifier: Modifier,
    onSelected: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    Column(modifier = modifier.padding(10.dp)) {
            Button(onClick = state::newDefinition, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text("新建 Agent")
            }
            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(state.definitions, key = { it.id }) { definition ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            scope.launch {
                                state.selectDefinition(definition.id)
                                onSelected()
                            }
                        },
                        color = if (state.definitionDraft?.id == definition.id) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    ) {
                        Column(modifier = Modifier.padding(9.dp)) {
                            Text(definition.name, fontWeight = FontWeight.Medium)
                            Text(definition.agentCode, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
    }
}

@Composable
private fun DefinitionContent(state: AgentWorkspaceState, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    Surface(
            modifier = modifier.padding(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = MaterialTheme.shapes.small,
        ) {
            state.definitionDraft?.let { command ->
                DefinitionEditor(
                    command = command,
                    dirty = state.dirty,
                    onChange = state::editDefinition,
                    onSave = { scope.launch { state.saveDefinition() } },
                    onDelete = { scope.launch { state.deleteDefinition() } },
                )
            } ?: Text("选择或新建 Agent", modifier = Modifier.padding(18.dp))
    }
}

@Composable
private fun DefinitionEditor(
    command: AgentDefinitionCommand,
    dirty: Boolean,
    onChange: (AgentDefinitionCommand) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    var schemaText by remember(command.id) { mutableStateOf(command.structuredOutput.schema.toString()) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Agent 定义", style = MaterialTheme.typography.titleLarge)
            Row {
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "删除 Agent") }
                Button(onClick = onSave, enabled = dirty) {
                    Icon(Icons.Outlined.Save, contentDescription = null)
                    Text("保存")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(command.agentCode, { onChange(command.copy(agentCode = it)) }, label = { Text("编码") }, modifier = Modifier.weight(1f))
            OutlinedTextField(command.name, { onChange(command.copy(name = it)) }, label = { Text("名称") }, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(command.modelCode, { onChange(command.copy(modelCode = it)) }, label = { Text("模型") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            command.instructions,
            { onChange(command.copy(instructions = it)) },
            label = { Text("指令") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 8,
        )
        OutlinedTextField(
            command.toolCodes.joinToString(", "),
            { value -> onChange(command.copy(toolCodes = value.split(',').map(String::trim).filter(String::isNotEmpty))) },
            label = { Text("工具编码") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            schemaText,
            { value ->
                schemaText = value
                runCatching { kotlinx.serialization.json.Json.parseToJsonElement(value).jsonObject }
                    .onSuccess { schema -> onChange(command.copy(structuredOutput = command.structuredOutput.copy(schema = schema))) }
            },
            label = { Text("结构化输出 JSON Schema") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 5,
        )
    }
}

@Composable
private fun ChatWorkspace(state: AgentWorkspaceState, compact: Boolean) {
    val scope = rememberCoroutineScope()
    if (compact) {
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        var pane by remember { mutableStateOf(CompactAgentPane.CHAT) }
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(modifier = Modifier.width(320.dp)) {
                    ConversationList(state, Modifier.fillMaxSize()) {
                        scope.launch { drawerState.close() }
                    }
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
                        Icon(Icons.Outlined.Menu, contentDescription = "打开会话目录")
                    }
                    CompactAgentPane.entries.forEach { item ->
                        FilterChip(
                            selected = pane == item,
                            onClick = { pane = item },
                            label = { Text(if (item == CompactAgentPane.CHAT) "对话" else "事件") },
                        )
                    }
                }
                when (pane) {
                    CompactAgentPane.CHAT -> ChatContent(state, Modifier.weight(1f))
                    CompactAgentPane.EVENTS -> EventList(state, Modifier.weight(1f))
                }
            }
        }
    } else {
        Row(modifier = Modifier.fillMaxSize()) {
            ConversationList(state, Modifier.width(230.dp).fillMaxHeight())
            ChatContent(state, Modifier.weight(1f).fillMaxHeight())
            EventList(state, Modifier.width(260.dp).fillMaxHeight())
        }
    }
}

@Composable
private fun ConversationList(
    state: AgentWorkspaceState,
    modifier: Modifier,
    onSelected: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    Column(modifier = modifier.padding(10.dp)) {
            Button(onClick = { scope.launch { state.createConversation() } }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text("新建会话")
            }
            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(state.conversations, key = { it.id }) { conversation ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            scope.launch {
                                state.selectConversation(conversation.id)
                                onSelected()
                            }
                        },
                        color = if (state.selectedConversationId == conversation.id) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(conversation.title, maxLines = 1)
                                Text(conversation.modelId.orEmpty(), style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick = { scope.launch { state.deleteConversation(conversation.id) } }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "删除会话")
                            }
                        }
                    }
                }
            }
    }
}

@Composable
private fun ChatContent(state: AgentWorkspaceState, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    Column(modifier = modifier.padding(10.dp)) {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.messages, key = { it.id }) { message ->
                    Text(message.role.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    message.parts.forEach { part ->
                        val patch = state.metadataPatch(part)
                        if (patch == null) {
                            val text = part["text"]?.jsonPrimitive?.contentOrNull
                                ?: part["content"]?.jsonPrimitive?.contentOrNull
                                ?: part.toString()
                            Text(text, style = MaterialTheme.typography.bodyMedium)
                        } else {
                            MetadataPatchPart(
                                command = patch,
                                applied = patch.key in state.appliedPatchKeys,
                                applying = state.loading,
                                onApply = { scope.launch { state.applyPatch(patch) } },
                            )
                        }
                    }
                }
                if (state.streamedText.isNotBlank()) {
                    item("stream") {
                        Text("ASSISTANT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text(state.streamedText)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = state.prompt,
                    onValueChange = state::updatePrompt,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入消息") },
                    minLines = 2,
                    maxLines = 5,
                )
                IconButton(onClick = {
                    if (state.streaming) scope.launch { state.cancel() } else scope.launch { state.send() }
                }) {
                    Icon(
                        if (state.streaming) Icons.Outlined.Stop else Icons.AutoMirrored.Outlined.Send,
                        contentDescription = if (state.streaming) "取消" else "发送",
                    )
                }
            }
    }
}

@Composable
private fun MetadataPatchPart(
    command: MetadataPatchCommand,
    applied: Boolean,
    applying: Boolean,
    onApply: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("元数据 Patch · ${command.patches.size} 项", fontWeight = FontWeight.Medium)
            Text(command.tableId, style = MaterialTheme.typography.labelSmall)
            if (command.questions.isNotEmpty()) {
                Text(command.questions.joinToString("；"), color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = onApply,
                enabled = !applied && !applying && command.questions.isEmpty() && command.patches.isNotEmpty(),
            ) {
                Text(if (applied) "已应用" else "应用 Patch")
            }
        }
    }
}

@Composable
private fun EventList(state: AgentWorkspaceState, modifier: Modifier) {
    Column(modifier = modifier.padding(10.dp)) {
            Text("流式事件", style = MaterialTheme.typography.titleSmall)
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.events) { event ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(event.event, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                        Text(event.data, style = MaterialTheme.typography.labelSmall, maxLines = 5)
                    }
                }
            }
    }
}

@Composable
private fun SettingsWorkspace(state: AgentWorkspaceState) {
    val scope = rememberCoroutineScope()
    var baseUrl by remember(state.settings) { mutableStateOf(state.settings?.baseUrl.orEmpty()) }
    var apiKey by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("模型提供商", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("Base URL") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(apiKey, { apiKey = it }, label = { Text("API Key") }, modifier = Modifier.fillMaxWidth())
        Text(if (state.settings?.apiKeyConfigured == true) "已配置 ${state.settings?.apiKeyMasked.orEmpty()}" else "未配置 API Key")
        Button(onClick = { scope.launch { state.updateSettings(baseUrl, apiKey.ifBlank { null }) } }) {
            Icon(Icons.Outlined.Save, contentDescription = null)
            Text("保存设置")
        }
        Text("可用模型：${state.models.joinToString { it.id }}")
    }
}

private val AgentSection.label: String
    get() = when (this) {
        AgentSection.DEFINITIONS -> "Agent 定义"
        AgentSection.CHAT -> "会话"
        AgentSection.SETTINGS -> "设置"
    }
