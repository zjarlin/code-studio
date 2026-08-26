package site.addzero.platform.lowcode.generator

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LowcodeWritePolicyCompilerTest {
    @Test
    fun `字段关联和计算属性写入策略编译到路由 LSI`() {
        val target = model(id = 2, modelCode = "department")
        val role = model(id = 3, modelCode = "role")
        val owner = model(
            id = 1,
            modelCode = "employee",
            fields = listOf(
                field(
                    createWritable = false,
                    updateWritable = true,
                ),
            ),
            relations = listOf(
                LowcodeRelationMeta(
                    id = 1,
                    modelId = 1,
                    orderNo = 1,
                    relationCode = "department",
                    label = "部门",
                    relationKind = LowcodeRelationKind.MANY_TO_ONE,
                    targetModelId = target.id,
                    targetModelCode = target.modelCode,
                    targetPackageName = target.packageName,
                    targetClassName = target.className,
                    joinColumn = "department_id",
                    mappedBy = null,
                    joinTable = null,
                    joinTableJoinColumn = null,
                    joinTableInverseColumn = null,
                    required = false,
                    listVisible = true,
                    formVisible = true,
                    createWritable = true,
                    updateWritable = false,
                ),
                LowcodeRelationMeta(
                    id = 2,
                    modelId = 1,
                    orderNo = 2,
                    relationCode = "children",
                    label = "下级",
                    relationKind = LowcodeRelationKind.ONE_TO_MANY,
                    targetModelId = 1,
                    targetModelCode = "employee",
                    targetPackageName = "example.employee",
                    targetClassName = "Employee",
                    joinColumn = null,
                    mappedBy = "department",
                    joinTable = null,
                    joinTableJoinColumn = null,
                    joinTableInverseColumn = null,
                    required = true,
                    listVisible = true,
                    formVisible = true,
                ),
                LowcodeRelationMeta(
                    id = 3,
                    modelId = 1,
                    orderNo = 3,
                    relationCode = "roles",
                    label = "角色",
                    relationKind = LowcodeRelationKind.MANY_TO_MANY,
                    targetModelId = role.id,
                    targetModelCode = role.modelCode,
                    targetPackageName = role.packageName,
                    targetClassName = role.className,
                    joinColumn = null,
                    mappedBy = null,
                    joinTable = "employee_role",
                    joinTableJoinColumn = "employee_id",
                    joinTableInverseColumn = "role_id",
                    required = true,
                    listVisible = true,
                    formVisible = true,
                ),
            ),
            entityConfig = LsiLowcodeEntityConfig(
                formulaProperties = listOf(
                    LsiLowcodeFormulaProperty(
                        propertyCode = "displayName",
                        label = "展示名称",
                        kotlinType = "String",
                        kind = LowcodeFormulaKind.KOTLIN,
                        expression = "name",
                    ),
                ),
                transientProperties = listOf(
                    LsiLowcodeTransientProperty(
                        propertyCode = "summary",
                        label = "摘要",
                        kotlinType = "String",
                    ),
                ),
            ),
        )

        val properties = LowcodeRouteCompiler.compile(owner, listOf(owner, target, role))
            .properties.associateBy(LsiLowcodeProperty::name)

        assertFalse(properties.getValue("name").createWritable)
        assertTrue(properties.getValue("name").updateWritable)
        listOf("department", "departmentId").forEach { propertyName ->
            assertTrue(properties.getValue(propertyName).createWritable)
            assertFalse(properties.getValue(propertyName).updateWritable)
        }
        listOf("displayName", "summary").forEach { propertyName ->
            assertFalse(properties.getValue(propertyName).createWritable)
            assertFalse(properties.getValue(propertyName).updateWritable)
        }
        listOf("children", "childrenIds", "roles", "roleIds").forEach { propertyName ->
            assertFalse(properties.getValue(propertyName).required)
        }
    }

    private fun model(
        id: Long,
        modelCode: String,
        fields: List<LowcodeFieldMeta> = emptyList(),
        relations: List<LowcodeRelationMeta> = emptyList(),
        entityConfig: LsiLowcodeEntityConfig = LsiLowcodeEntityConfig(),
    ): LowcodeModelMeta = LowcodeModelMeta(
        id = id,
        modelCode = modelCode,
        name = modelCode,
        packageName = "example.$modelCode",
        className = modelCode.replaceFirstChar(Char::uppercaseChar),
        tableName = modelCode,
        kind = LowcodeModelKind.ENTITY,
        status = 1,
        version = 1,
        entityConfig = entityConfig,
        fields = fields,
        queries = emptyList(),
        relations = relations,
    )

    private fun field(
        createWritable: Boolean,
        updateWritable: Boolean,
    ): LowcodeFieldMeta = LowcodeFieldMeta(
        id = 1,
        modelId = 1,
        orderNo = 1,
        fieldCode = "name",
        label = "名称",
        kotlinType = "String",
        dbColumn = "name",
        required = true,
        listVisible = true,
        formVisible = true,
        formControl = "input",
        dictCode = null,
        defaultValue = null,
        remark = null,
        createWritable = createWritable,
        updateWritable = updateWritable,
    )
}
