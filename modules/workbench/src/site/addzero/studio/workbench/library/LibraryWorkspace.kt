package site.addzero.studio.workbench.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import site.addzero.studio.contract.PreviewFile
import site.addzero.studio.workbench.components.editor.EditorFrame

@Composable
fun LibraryWorkspace(state: LibraryWorkspaceState = koinInject()) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { state.load() }

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            if (maxWidth < 760.dp) {
                CompactLibraryWorkspace(state)
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    LibraryNavigation(state, modifier = Modifier.fillMaxHeight())
                    ResourceEditorSurface(state, Modifier.weight(1f))
                }
            }
        }
        if (state.loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }

    if (state.pendingSelection != null) {
        AlertDialog(
            onDismissRequest = state::keepEditing,
            title = { Text("放弃未保存修改？") },
            text = { Text("当前草稿已修改，切换资源会丢弃这些内容。") },
            confirmButton = { TextButton(onClick = state::discardAndSelect) { Text("放弃并切换") } },
            dismissButton = { TextButton(onClick = state::keepEditing) { Text("继续编辑") } },
        )
    }
    if (state.previewFiles.isNotEmpty()) {
        PreviewDialog(files = state.previewFiles, onClose = state::closePreview)
    }
}

@Composable
private fun CompactLibraryWorkspace(state: LibraryWorkspaceState) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(340.dp)) {
                LibraryNavigation(
                    state = state,
                    compact = true,
                    onSelected = { scope.launch { drawerState.close() } },
                )
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                Icon(Icons.Outlined.Menu, contentDescription = "打开 Library 目录")
            }
            ResourceEditorSurface(state, Modifier.weight(1f))
        }
    }
}

@Composable
private fun LibraryNavigation(
    state: LibraryWorkspaceState,
    compact: Boolean = false,
    onSelected: () -> Unit = {},
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    val scope = rememberCoroutineScope()
    val libraryPane: @Composable (Modifier) -> Unit = { modifier ->
        LibraryPane(
            state = state,
            modifier = modifier,
            onSelectLibrary = { id -> scope.launch { state.selectLibrary(id); onSelected() } },
            onSelectFeature = { id -> scope.launch { state.selectFeature(id); onSelected() } },
        )
    }
    if (compact) {
        Column(modifier = modifier) {
            libraryPane(Modifier.weight(1f).fillMaxWidth())
            ResourcePane(state, onSelected, Modifier.weight(1f).fillMaxWidth())
        }
    } else {
        Row(modifier = modifier) {
            libraryPane(Modifier.width(220.dp).fillMaxHeight())
            ResourcePane(state, onSelected, Modifier.width(240.dp).fillMaxHeight())
        }
    }
}

@Composable
private fun ResourceEditorSurface(state: LibraryWorkspaceState, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    Surface(
        modifier = modifier.fillMaxHeight().padding(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(6.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            ResourceEditor(
                state = state,
                onSave = { scope.launch { state.save() } },
                onDelete = { scope.launch { state.deleteSelected() } },
                onPreview = { scope.launch { state.preview() } },
            )
        }
    }
}

@Composable
private fun LibraryPane(
    state: LibraryWorkspaceState,
    modifier: Modifier,
    onSelectLibrary: (Long) -> Unit,
    onSelectFeature: (Long) -> Unit,
) {
    Column(
        modifier = modifier.padding(vertical = 10.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PaneTitle("Libraries") { state.newResource(LibraryResourceKind.LIBRARY) }
        LazyColumn(modifier = Modifier.weight(0.42f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(state.libraries, key = { it.id }) { library ->
                NavigationRow(
                    label = library.displayName,
                    detail = library.code,
                    selected = state.selectedLibraryId == library.id,
                    onClick = { onSelectLibrary(library.id) },
                )
            }
        }
        PaneTitle("功能目录") { state.newResource(LibraryResourceKind.FEATURE) }
        LazyColumn(modifier = Modifier.weight(0.58f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(state.features, key = { it.id }) { feature ->
                NavigationRow(
                    label = feature.name,
                    detail = feature.featureCode,
                    selected = state.selectedFeatureId == feature.id,
                    onClick = { onSelectFeature(feature.id) },
                )
            }
        }
    }
}

@Composable
private fun ResourcePane(
    state: LibraryWorkspaceState,
    onSelected: () -> Unit = {},
    modifier: Modifier = Modifier.width(240.dp).fillMaxHeight(),
) {
    val rows = buildList {
        addAll(state.models.map { ResourceRow(LibraryResourceKind.MODEL, it.id, it.name, it.modelCode) })
        addAll(state.dtos.map { ResourceRow(LibraryResourceKind.DTO, it.id, it.name, it.dtoCode) })
        addAll(state.constants.map { ResourceRow(LibraryResourceKind.CONSTANT, it.id, it.objectName, it.groupCode) })
        addAll(state.conventionFiles.map { ResourceRow(LibraryResourceKind.CONVENTION_FILE, it.id, it.name, it.className) })
    }
    Column(
        modifier = modifier.padding(vertical = 10.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("资源", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                LibraryResourceKind.MODEL to "M",
                LibraryResourceKind.DTO to "D",
                LibraryResourceKind.CONSTANT to "C",
                LibraryResourceKind.CONVENTION_FILE to "S/J",
            ).forEach { (kind, label) ->
                OutlinedButton(
                    onClick = { state.newResource(kind) },
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp),
                ) {
                    Text(label)
                }
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(rows, key = { "${it.kind}:${it.id}" }) { row ->
                NavigationRow(
                    label = row.label,
                    detail = "${row.kind.label} · ${row.detail}",
                    selected = state.selection.kind == row.kind && state.selection.id == row.id,
                    onClick = {
                        if (state.requestSelection(ResourceSelection(row.kind, row.id))) onSelected()
                    },
                )
            }
        }
    }
}

@Composable
private fun ResourceEditor(
    state: LibraryWorkspaceState,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onPreview: () -> Unit,
) {
    state.error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
    }
    val title = state.selection.kind.label
    val subtitle = if (state.dirty) "已修改" else "已保存"
    EditorFrame(
        title = title,
        subtitle = subtitle,
        dirty = state.dirty,
        saving = state.saving,
        canPreview = state.selection.id != null && state.selection.kind !in setOf(
            LibraryResourceKind.CONSTANT,
            LibraryResourceKind.CONVENTION_FILE,
        ),
        validation = state.validation,
        onSave = onSave,
        onDelete = onDelete,
        onPreview = onPreview,
    ) {
        when (state.selection.kind) {
            LibraryResourceKind.LIBRARY -> state.libraryDraft?.let { LibraryEditor(it) { value -> state.editLibrary { value } } }
            LibraryResourceKind.FEATURE -> state.featureDraft?.let { FeatureEditor(it) { value -> state.editFeature { value } } }
            LibraryResourceKind.MODEL -> state.modelDraft?.let { ModelEditor(it) { value -> state.editModel { value } } }
            LibraryResourceKind.DTO -> state.dtoDraft?.let { DtoEditor(it) { value -> state.editDto { value } } }
            LibraryResourceKind.CONSTANT -> state.constantDraft?.let { ConstantEditor(it) { value -> state.editConstant { value } } }
            LibraryResourceKind.CONVENTION_FILE -> state.conventionFileDraft?.let {
                ConventionFileEditor(it) { value -> state.editConventionFile { value } }
            }
        } ?: Text("选择或新建资源开始编辑")
    }
}

@Composable
private fun PaneTitle(title: String, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        IconButton(onClick = onAdd) { Icon(Icons.Outlined.Add, contentDescription = "新增$title") }
    }
}

@Composable
private fun NavigationRow(
    label: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(4.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label.ifBlank { "未命名" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PreviewDialog(files: List<PreviewFile>, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("生成预览 · ${files.size} 个文件") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(files, key = PreviewFile::filePath) { file ->
                    Column {
                        Text(file.filePath, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(4.dp)) {
                            Text(file.content, modifier = Modifier.fillMaxWidth().padding(10.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onClose) { Text("关闭") } },
    )
}

private data class ResourceRow(
    val kind: LibraryResourceKind,
    val id: Long?,
    val label: String,
    val detail: String,
)

private val LibraryResourceKind.label: String
    get() = when (this) {
        LibraryResourceKind.LIBRARY -> "Library"
        LibraryResourceKind.FEATURE -> "功能目录"
        LibraryResourceKind.MODEL -> "模型"
        LibraryResourceKind.DTO -> "DTO"
        LibraryResourceKind.CONSTANT -> "常量"
        LibraryResourceKind.CONVENTION_FILE -> "Service / 定时任务"
    }
