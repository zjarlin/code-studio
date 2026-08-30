package site.addzero.studio.workbench.library

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import site.addzero.studio.contract.ConventionFileCommand
import site.addzero.studio.contract.ConventionFileKind

@Composable
internal fun ConventionFileEditor(
    command: ConventionFileCommand,
    onChange: (ConventionFileCommand) -> Unit,
) {
    Section("文件约定") {
        FormRow {
            ConventionFileKind.entries.forEach { kind ->
                FilterChip(
                    selected = command.kind == kind,
                    onClick = { onChange(command.copy(kind = kind)) },
                    label = { Text(if (kind == ConventionFileKind.SERVICE) "Service" else "定时任务") },
                )
            }
        }
        FormRow {
            LabeledField("内部标识", command.fileCode, { onChange(command.copy(fileCode = it)) }, Modifier.weight(1f))
            LabeledField("名称", command.name, { onChange(command.copy(name = it)) }, Modifier.weight(1f))
        }
        LabeledField("类名", command.className, { onChange(command.copy(className = it)) }, Modifier.fillMaxWidth())
        LabeledField(
            "说明",
            command.description.orEmpty(),
            { onChange(command.copy(description = it.ifBlank { null })) },
            Modifier.fillMaxWidth(),
            singleLine = false,
        )
    }
    Text(
        "代码由 IDE 维护，Studio 只管理文件名、类名、归属、路径和状态。",
        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
