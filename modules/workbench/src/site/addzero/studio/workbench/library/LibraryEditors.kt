package site.addzero.studio.workbench.library

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import site.addzero.studio.contract.LibraryCommand
import site.addzero.studio.contract.LibraryFeatureCommand
import site.addzero.studio.workbench.components.editor.FormRow
import site.addzero.studio.workbench.components.editor.LabeledField
import site.addzero.studio.workbench.components.editor.Section

@Composable
internal fun LibraryEditor(command: LibraryCommand, onChange: (LibraryCommand) -> Unit) {
    Section("库标识") {
        FormRow {
            LabeledField("标识", command.code, { onChange(command.copy(code = it)) }, Modifier.weight(1f))
            LabeledField("名称", command.displayName, { onChange(command.copy(displayName = it)) }, Modifier.weight(1f))
        }
        FormRow {
            LabeledField(
                "版本",
                command.version.toString(),
                { value -> value.toIntOrNull()?.let { onChange(command.copy(version = it)) } },
                Modifier.weight(1f),
            )
            LabeledField(
                "状态",
                command.status.toString(),
                { value -> value.toIntOrNull()?.let { onChange(command.copy(status = it)) } },
                Modifier.weight(1f),
            )
        }
    }
    Section("代码归属") {
        LabeledField(
            "包前缀",
            command.spec.packagePrefix,
            { onChange(command.copy(spec = command.spec.copy(packagePrefix = it))) },
            Modifier.fillMaxWidth(),
        )
        LabeledField(
            "扫描包",
            command.spec.scanPackage,
            { onChange(command.copy(spec = command.spec.copy(scanPackage = it))) },
            Modifier.fillMaxWidth(),
        )
        LabeledField(
            "说明",
            command.spec.description.orEmpty(),
            { onChange(command.copy(spec = command.spec.copy(description = it.ifBlank { null }))) },
            Modifier.fillMaxWidth(),
            singleLine = false,
        )
    }
    Section("应用能力") {
        FormRow {
            Checkbox(
                checked = command.spec.applicationSelectable,
                onCheckedChange = { onChange(command.copy(spec = command.spec.copy(applicationSelectable = it))) },
            )
            Text("允许宿主应用选择", modifier = Modifier.align(Alignment.CenterVertically))
        }
    }
}

@Composable
internal fun FeatureEditor(command: LibraryFeatureCommand, onChange: (LibraryFeatureCommand) -> Unit) {
    Section("功能目录") {
        FormRow {
            LabeledField("编码", command.featureCode, { onChange(command.copy(featureCode = it)) }, Modifier.weight(1f))
            LabeledField("名称", command.name, { onChange(command.copy(name = it)) }, Modifier.weight(1f))
        }
        LabeledField(
            "说明",
            command.description.orEmpty(),
            { onChange(command.copy(description = it.ifBlank { null })) },
            Modifier.fillMaxWidth(),
            singleLine = false,
        )
    }
}
