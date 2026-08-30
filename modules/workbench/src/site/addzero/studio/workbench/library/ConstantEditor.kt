package site.addzero.studio.workbench.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import site.addzero.studio.contract.ConstantCommand
import site.addzero.studio.contract.ConstantItemCommand
import site.addzero.studio.workbench.components.editor.FormRow
import site.addzero.studio.workbench.components.editor.LabeledField
import site.addzero.studio.workbench.components.editor.Section

@Composable
internal fun ConstantEditor(command: ConstantCommand, onChange: (ConstantCommand) -> Unit) {
    Section("常量组") {
        FormRow {
            LabeledField("编码", command.groupCode, { onChange(command.copy(groupCode = it)) }, Modifier.weight(1f))
            LabeledField("对象名", command.objectName, { onChange(command.copy(objectName = it)) }, Modifier.weight(1f))
        }
        LabeledField("说明", command.description, { onChange(command.copy(description = it)) }, Modifier.fillMaxWidth())
    }
    Section("常量项") {
        Button(onClick = {
            onChange(command.copy(constants = command.constants + ConstantItemCommand(
                name = "",
                type = "STRING",
                value = "",
                description = "",
            )))
        }) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Text("新增常量")
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            command.constants.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LabeledField("名称", item.name, { updateItem(command, index, item.copy(name = it), onChange) }, Modifier.weight(1f))
                    LabeledField("类型", item.type, { updateItem(command, index, item.copy(type = it), onChange) }, Modifier.weight(0.7f))
                    LabeledField("值", item.value, { updateItem(command, index, item.copy(value = it), onChange) }, Modifier.weight(1f))
                    LabeledField("说明", item.description, { updateItem(command, index, item.copy(description = it), onChange) }, Modifier.weight(1.2f))
                    IconButton(onClick = {
                        onChange(command.copy(constants = command.constants.filterIndexed { i, _ -> i != index }))
                    }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "删除常量")
                    }
                }
            }
        }
    }
}

private fun updateItem(
    command: ConstantCommand,
    index: Int,
    item: ConstantItemCommand,
    onChange: (ConstantCommand) -> Unit,
) {
    onChange(command.copy(constants = command.constants.mapIndexed { i, value -> if (i == index) item else value }))
}
