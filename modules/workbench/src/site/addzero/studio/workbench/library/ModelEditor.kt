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
import site.addzero.studio.contract.CustomOperationCommand
import site.addzero.studio.contract.FieldCommand
import site.addzero.studio.contract.HttpMethod
import site.addzero.studio.contract.ModelCommand
import site.addzero.studio.contract.QueryCommand
import site.addzero.studio.contract.QueryLogic
import site.addzero.studio.contract.RelationCommand
import site.addzero.studio.contract.RelationKind
import site.addzero.studio.workbench.components.editor.FormRow
import site.addzero.studio.workbench.components.editor.LabeledField
import site.addzero.studio.workbench.components.editor.Section
import site.addzero.studio.workbench.components.table.DataColumn
import site.addzero.studio.workbench.components.table.DataTable

private enum class ModelSection { MODEL, FIELDS, RELATIONS, QUERIES, API }

@Composable
internal fun ModelEditor(command: ModelCommand, onChange: (ModelCommand) -> Unit) {
    var section by remember { mutableStateOf(ModelSection.MODEL) }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ModelSection.entries.forEach { item ->
            FilterChip(
                selected = section == item,
                onClick = { section = item },
                label = { Text(item.label) },
            )
        }
    }
    when (section) {
        ModelSection.MODEL -> ModelIdentityEditor(command, onChange)
        ModelSection.FIELDS -> FieldEditor(command, onChange)
        ModelSection.RELATIONS -> RelationEditor(command, onChange)
        ModelSection.QUERIES -> QueryEditor(command, onChange)
        ModelSection.API -> RouteEditor(command, onChange)
    }
}

@Composable
private fun ModelIdentityEditor(command: ModelCommand, onChange: (ModelCommand) -> Unit) {
    Section("模型") {
        FormRow {
            LabeledField("编码", command.modelCode, { onChange(command.copy(modelCode = it)) }, Modifier.weight(1f))
            LabeledField("名称", command.name, { onChange(command.copy(name = it)) }, Modifier.weight(1f))
        }
        FormRow {
            LabeledField("类名", command.className, { onChange(command.copy(className = it)) }, Modifier.weight(1f))
            LabeledField("表名", command.tableName, { onChange(command.copy(tableName = it)) }, Modifier.weight(1f))
        }
        LabeledField(
            "说明",
            command.remark.orEmpty(),
            { onChange(command.copy(remark = it.ifBlank { null })) },
            Modifier.fillMaxWidth(),
            singleLine = false,
        )
    }
    Section("继承") {
        Text("基模式：${command.entityConfig.baseMode}")
        Text("基模型：${command.entityConfig.baseModels.joinToString().ifBlank { "无" }}")
        LabeledField(
            "已有实体限定名",
            command.entityConfig.sourceQualifiedName.orEmpty(),
            { onChange(command.copy(entityConfig = command.entityConfig.copy(sourceQualifiedName = it.ifBlank { null }))) },
            Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FieldEditor(command: ModelCommand, onChange: (ModelCommand) -> Unit) {
    var selected by remember(command.id) { mutableIntStateOf(-1) }
    val columns = remember {
        listOf(
            DataColumn("fieldCode", "字段", value = FieldCommand::fieldCode),
            DataColumn("label", "名称", value = FieldCommand::label),
            DataColumn("kotlinType", "Kotlin 类型", value = FieldCommand::kotlinType),
            DataColumn("dbColumn", "数据库列", value = FieldCommand::dbColumn),
            DataColumn("required", "必填", width = 90f) { field -> if (field.required) "是" else "否" },
        )
    }
    Section("字段") {
        Button(
            onClick = {
                val next = command.fields + FieldCommand(
                    orderNo = command.fields.size + 1,
                    fieldCode = "",
                    label = "",
                    kotlinType = "kotlin.String",
                    dbColumn = "",
                )
                onChange(command.copy(fields = next))
                selected = next.lastIndex
            },
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Text("新增字段")
        }
        DataTable(
            data = command.fields,
            columns = columns,
            actions = { _, index ->
                IconButton(onClick = { selected = index }) {
                    Icon(Icons.Outlined.Edit, contentDescription = "编辑字段")
                }
                IconButton(onClick = {
                    onChange(command.copy(fields = command.fields.filterIndexed { itemIndex, _ -> itemIndex != index }))
                    selected = -1
                }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "删除字段")
                }
            },
            modifier = Modifier.fillMaxWidth().height(270.dp),
        )
    }
    command.fields.getOrNull(selected)?.let { field ->
        Section("字段详细") {
            FormRow {
                LabeledField("编码", field.fieldCode, { updateField(command, selected, field.copy(fieldCode = it), onChange) }, Modifier.weight(1f))
                LabeledField("名称", field.label, { updateField(command, selected, field.copy(label = it), onChange) }, Modifier.weight(1f))
            }
            FormRow {
                LabeledField("类型", field.kotlinType, { updateField(command, selected, field.copy(kotlinType = it), onChange) }, Modifier.weight(1f))
                LabeledField("列名", field.dbColumn, { updateField(command, selected, field.copy(dbColumn = it), onChange) }, Modifier.weight(1f))
            }
            LabeledField(
                "说明",
                field.remark.orEmpty(),
                { updateField(command, selected, field.copy(remark = it.ifBlank { null }), onChange) },
                Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RelationEditor(command: ModelCommand, onChange: (ModelCommand) -> Unit) {
    CollectionEditor(
        title = "关系",
        labels = command.relations.map { it.relationCode.ifBlank { "未命名关系" } },
        onAdd = {
            onChange(command.copy(relations = command.relations + RelationCommand(
                orderNo = command.relations.size + 1,
                relationCode = "",
                label = "",
                relationType = RelationKind.MANY_TO_ONE,
            )))
        },
        onDelete = { index -> onChange(command.copy(relations = command.relations.filterIndexed { i, _ -> i != index })) },
    ) { index ->
        val relation = command.relations[index]
        FormRow {
            LabeledField("编码", relation.relationCode, { updateRelation(command, index, relation.copy(relationCode = it), onChange) }, Modifier.weight(1f))
            LabeledField("名称", relation.label, { updateRelation(command, index, relation.copy(label = it), onChange) }, Modifier.weight(1f))
        }
        FormRow {
            LabeledField("目标模型", relation.targetModelCode.orEmpty(), { updateRelation(command, index, relation.copy(targetModelCode = it.ifBlank { null }), onChange) }, Modifier.weight(1f))
            LabeledField("关联列", relation.joinColumn.orEmpty(), { updateRelation(command, index, relation.copy(joinColumn = it.ifBlank { null }), onChange) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun QueryEditor(command: ModelCommand, onChange: (ModelCommand) -> Unit) {
    CollectionEditor(
        title = "查询",
        labels = command.queries.map { it.queryCode.ifBlank { "未命名查询" } },
        onAdd = {
            onChange(command.copy(queries = command.queries + QueryCommand(
                orderNo = command.queries.size + 1,
                queryCode = "",
                label = "",
                logic = QueryLogic.AND,
            )))
        },
        onDelete = { index -> onChange(command.copy(queries = command.queries.filterIndexed { i, _ -> i != index })) },
    ) { index ->
        val query = command.queries[index]
        FormRow {
            LabeledField("编码", query.queryCode, { updateQuery(command, index, query.copy(queryCode = it), onChange) }, Modifier.weight(1f))
            LabeledField("名称", query.label, { updateQuery(command, index, query.copy(label = it), onChange) }, Modifier.weight(1f))
        }
        Text("条件数：${query.items.size}")
    }
}

@Composable
private fun RouteEditor(command: ModelCommand, onChange: (ModelCommand) -> Unit) {
    val route = command.routeConfig ?: return
    Section("Controller") {
        LabeledField("路径", route.path, { onChange(command.copy(routeConfig = route.copy(path = it))) }, Modifier.fillMaxWidth())
        CollectionEditor(
            title = "自定义操作",
            labels = route.customOperations.map { it.operationCode.ifBlank { "未命名操作" } },
            onAdd = {
                val operations = route.customOperations + CustomOperationCommand(
                    operationCode = "",
                    name = "",
                    path = "",
                    method = HttpMethod.POST,
                )
                onChange(command.copy(routeConfig = route.copy(customOperations = operations)))
            },
            onDelete = { index ->
                onChange(command.copy(routeConfig = route.copy(
                    customOperations = route.customOperations.filterIndexed { i, _ -> i != index },
                )))
            },
        ) { index ->
            val operation = route.customOperations[index]
            FormRow {
                LabeledField("编码", operation.operationCode, { value ->
                    updateOperation(command, index, operation.copy(operationCode = value), onChange)
                }, Modifier.weight(1f))
                LabeledField("名称", operation.name, { value ->
                    updateOperation(command, index, operation.copy(name = value), onChange)
                }, Modifier.weight(1f))
            }
            LabeledField("路径", operation.path, { value ->
                updateOperation(command, index, operation.copy(path = value), onChange)
            }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CollectionEditor(
    title: String,
    labels: List<String>,
    onAdd: () -> Unit,
    onDelete: (Int) -> Unit,
    content: @Composable (Int) -> Unit,
) {
    var selected by remember(labels.size) { mutableIntStateOf(if (labels.isEmpty()) -1 else 0) }
    Section(title) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            labels.forEachIndexed { index, label ->
                FilterChip(selected = selected == index, onClick = { selected = index }, label = { Text(label) })
            }
            IconButton(onClick = onAdd) { Icon(Icons.Outlined.Add, contentDescription = "新增$title") }
            if (selected in labels.indices) {
                IconButton(onClick = { onDelete(selected); selected = -1 }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "删除$title")
                }
            }
        }
        if (selected in labels.indices) content(selected)
    }
}

private fun updateField(command: ModelCommand, index: Int, field: FieldCommand, onChange: (ModelCommand) -> Unit) {
    onChange(command.copy(fields = command.fields.mapIndexed { i, value -> if (i == index) field else value }))
}

private fun updateRelation(command: ModelCommand, index: Int, relation: RelationCommand, onChange: (ModelCommand) -> Unit) {
    onChange(command.copy(relations = command.relations.mapIndexed { i, value -> if (i == index) relation else value }))
}

private fun updateQuery(command: ModelCommand, index: Int, query: QueryCommand, onChange: (ModelCommand) -> Unit) {
    onChange(command.copy(queries = command.queries.mapIndexed { i, value -> if (i == index) query else value }))
}

private fun updateOperation(
    command: ModelCommand,
    index: Int,
    operation: CustomOperationCommand,
    onChange: (ModelCommand) -> Unit,
) {
    val route = command.routeConfig ?: return
    onChange(command.copy(routeConfig = route.copy(
        customOperations = route.customOperations.mapIndexed { i, value -> if (i == index) operation else value },
    )))
}

private val ModelSection.label: String
    get() = when (this) {
        ModelSection.MODEL -> "模型"
        ModelSection.FIELDS -> "字段"
        ModelSection.RELATIONS -> "关系"
        ModelSection.QUERIES -> "查询"
        ModelSection.API -> "Controller"
    }
