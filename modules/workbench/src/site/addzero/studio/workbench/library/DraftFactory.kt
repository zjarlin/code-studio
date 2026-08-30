package site.addzero.studio.workbench.library

import site.addzero.studio.contract.ConstantCommand
import site.addzero.studio.contract.ConventionFileCommand
import site.addzero.studio.contract.ConventionFileKind
import site.addzero.studio.contract.DtoCommand
import site.addzero.studio.contract.DtoKind
import site.addzero.studio.contract.EntityConfigCommand
import site.addzero.studio.contract.ModelCommand
import site.addzero.studio.contract.RouteCommand

internal fun newModelDraft(featureId: Long) = ModelCommand(
    featureId = featureId,
    modelCode = "",
    name = "",
    className = "",
    tableName = "",
    entityConfig = EntityConfigCommand(),
    routeConfig = RouteCommand(path = ""),
)

internal fun newDtoDraft(featureId: Long) = DtoCommand(
    featureId = featureId,
    dtoCode = "",
    name = "",
    className = "",
    kind = DtoKind.INPUT,
)

internal fun newConventionFileDraft(featureId: Long) = ConventionFileCommand(
    featureId = featureId,
    fileCode = "",
    name = "",
    className = "",
    kind = ConventionFileKind.SERVICE,
)

internal fun newConstantDraft(featureId: Long) = ConstantCommand(
    featureId = featureId,
    groupCode = "",
    objectName = "",
    description = "",
    constants = emptyList(),
)
