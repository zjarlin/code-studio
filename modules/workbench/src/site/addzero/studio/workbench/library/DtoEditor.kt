package site.addzero.studio.workbench.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import site.addzero.studio.contract.DtoCommand
import site.addzero.studio.contract.DtoFieldCommand
import site.addzero.studio.contract.DtoKind
import site.addzero.studio.contract.DtoNullability
import site.addzero.studio.contract.DtoSelectionMode

@Composable
internal fun DtoEditor(command: DtoCommand, onChange: (DtoCommand) -> Unit) {
    Section("DTO") {
        FormRow {
            LabeledField("编码", command.dtoCode, { onChange(command.copy(dtoCode = it)) }, Modifier.weight(1f))
            LabeledField("名称", command.name, { onChange(command.copy(name = it)) }, Modifier.weight(1f))
        }
        FormRow {
            LabeledField("类名", command.className, { onChange(command.copy(className = it)) }, Modifier.weight(1f))
            LabeledField(
                "源模型",
                command.sourceModelCode.orEmpty(),
                { onChange(command.copy(sourceModelCode = it.ifBlank { null })) },
                Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DtoKind.entries.forEach { kind ->
                FilterChip(selected = command.kind == kind, onClick = { onChange(command.copy(kind = kind)) }, label = { Text(kind.name) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DtoSelectionMode.entries.forEach { mode ->
                FilterChip(
                    selected = command.selectionMode == mode,
                    onClick = { onChange(command.copy(selectionMode = mode)) },
                    label = { Text(mode.name.removePrefix("ALL_").replace('_', ' ')) },
                )
            }
        }
        LabeledField(
            "说明",
            command.description.orEmpty(),
            { onChange(command.copy(description = it.ifBlank { null })) },
            Modifier.fillMaxWidth(),
            singleLine = false,
        )
    }
    DtoFields(command, onChange)
}

@Composable
private fun DtoFields(command: DtoCommand, onChange: (DtoCommand) -> Unit) {
    var selected by remember(command.id) { mutableIntStateOf(-1) }
    val columns = remember { listOf("name", "sourcePath", "nullability", "description") }
    Section("字段") {
        Button(onClick = {
            val fields = command.fields + DtoFieldCommand(name = "", sourcePath = "")
            onChange(command.copy(fields = fields))
            selected = fields.lastIndex
        }) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Text("新增字段")
        }
        MetadataTable(
            data = command.fields,
            columns = columns.map { MetadataColumn(it, it, width = 160) },
            cell = { field, column -> when (column) {
                "name" -> field.name
                "sourcePath" -> field.sourcePath
                "nullability" -> field.nullability.name
                "description" -> field.description.orEmpty()
                else -> ""
            } },
            actions = { _, index ->
                IconButton(onClick = { selected = index }) {
                    Icon(Icons.Outlined.Edit, contentDescription = "编辑 DTO 字段")
                }
                IconButton(onClick = {
                    onChange(command.copy(fields = command.fields.filterIndexed { i, _ -> i != index }))
                    selected = -1
                }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "删除 DTO 字段")
                }
            },
            modifier = Modifier.fillMaxWidth().height(260.dp),
        )
    }
    command.fields.getOrNull(selected)?.let { field ->
        Section("字段详细") {
            FormRow {
                LabeledField("名称", field.name, { updateField(command, selected, field.copy(name = it), onChange) }, Modifier.weight(1f))
                LabeledField("源路径", field.sourcePath, { updateField(command, selected, field.copy(sourcePath = it), onChange) }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DtoNullability.entries.forEach { nullability ->
                    FilterChip(
                        selected = field.nullability == nullability,
                        onClick = { updateField(command, selected, field.copy(nullability = nullability), onChange) },
                        label = { Text(nullability.name) },
                    )
                }
            }
            LabeledField(
                "说明",
                field.description.orEmpty(),
                { updateField(command, selected, field.copy(description = it.ifBlank { null }), onChange) },
                Modifier.fillMaxWidth(),
            )
            Text("校验规则：${field.validations.size}  注解：${field.annotations.size}")
        }
    }
}

private fun updateField(command: DtoCommand, index: Int, field: DtoFieldCommand, onChange: (DtoCommand) -> Unit) {
    onChange(command.copy(fields = command.fields.mapIndexed { i, value -> if (i == index) field else value }))
}
