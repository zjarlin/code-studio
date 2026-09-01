package site.addzero.studio.clientcontract

import io.ktor.openapi.PathItem
import io.ktor.openapi.ReferenceOr
import kotlinx.serialization.json.JsonElement
import site.addzero.studio.contract.CommonResult
import site.addzero.studio.contract.ConstantCommand
import site.addzero.studio.contract.ConstantListCommand
import site.addzero.studio.contract.ConstantView
import site.addzero.studio.contract.ConventionFileCommand
import site.addzero.studio.contract.ConventionFileView
import site.addzero.studio.contract.DtoCommand
import site.addzero.studio.contract.DtoPreview
import site.addzero.studio.contract.LibraryCommand
import site.addzero.studio.contract.LibraryFeatureCommand
import site.addzero.studio.contract.LibraryFeaturePage
import site.addzero.studio.contract.LibraryFeatureView
import site.addzero.studio.contract.LibraryPage
import site.addzero.studio.contract.LibraryPreview
import site.addzero.studio.contract.LibraryView
import site.addzero.studio.contract.MetadataValidationResult
import site.addzero.studio.contract.ModelCommand
import site.addzero.studio.contract.ModelPageCommand
import site.addzero.studio.contract.ModelPreview
import site.addzero.studio.contract.PageResult
import site.addzero.validation.compiler.LsiValidationRuleMetadata

internal fun metadataClientPaths(): Map<String, ReferenceOr<PathItem>> = linkedMapOf(
    "/studio/api/lowcode/library/page" to pathItem(
        get = operation("listLibraries", "Library") {
            pageParameters()
            jsonResponse<CommonResult<LibraryPage>>()
        },
    ),
    "/studio/api/lowcode/library/detail" to pathItem(
        get = operation("getLibrary", "Library") {
            longQueryParameter("id", required = true)
            jsonResponse<CommonResult<LibraryView>>()
        },
    ),
    "/studio/api/lowcode/library/validate" to pathItem(
        post = operation("validateLibrary", "Library") {
            jsonBody<LibraryCommand>()
            jsonResponse<CommonResult<MetadataValidationResult>>()
        },
    ),
    "/studio/api/lowcode/library/add" to pathItem(
        post = operation("addLibrary", "Library") {
            jsonBody<LibraryCommand>()
            jsonResponse<CommonResult<Long>>()
        },
    ),
    "/studio/api/lowcode/library/update" to pathItem(
        put = operation("updateLibrary", "Library") {
            jsonBody<LibraryCommand>()
            jsonResponse<CommonResult<Boolean>>()
        },
    ),
    "/studio/api/lowcode/library/delete" to pathItem(
        delete = operation("deleteLibraries", "Library") {
            jsonBody<List<Long>>()
            jsonResponse<CommonResult<Boolean>>()
        },
    ),
    "/studio/api/lowcode/library/preview" to pathItem(
        get = operation("previewLibrary", "Library") {
            longQueryParameter("id", required = true)
            longQueryParameter("featureId")
            jsonResponse<CommonResult<LibraryPreview>>()
        },
    ),
    "/studio/api/lowcode/library-feature/page" to pathItem(
        get = operation("listLibraryFeatures", "Library Feature") {
            pageParameters()
            longQueryParameter("libraryId")
            jsonResponse<CommonResult<LibraryFeaturePage>>()
        },
    ),
    "/studio/api/lowcode/library-feature/detail" to pathItem(
        get = operation("getLibraryFeature", "Library Feature") {
            longQueryParameter("id", required = true)
            jsonResponse<CommonResult<LibraryFeatureView>>()
        },
    ),
    "/studio/api/lowcode/library-feature/validate" to pathItem(
        post = operation("validateLibraryFeature", "Library Feature") {
            jsonBody<LibraryFeatureCommand>()
            jsonResponse<CommonResult<MetadataValidationResult>>()
        },
    ),
    "/studio/api/lowcode/library-feature/create" to pathItem(
        post = operation("createLibraryFeature", "Library Feature") {
            jsonBody<LibraryFeatureCommand>()
            jsonResponse<CommonResult<LibraryFeatureView>>()
        },
    ),
    "/studio/api/lowcode/library-feature/update" to pathItem(
        put = operation("updateLibraryFeature", "Library Feature") {
            jsonBody<LibraryFeatureCommand>()
            jsonResponse<CommonResult<LibraryFeatureView>>()
        },
    ),
    "/studio/api/lowcode/library-feature/delete" to pathItem(
        delete = operation("deleteLibraryFeature", "Library Feature") {
            longQueryParameter("id", required = true)
            jsonResponse<CommonResult<Boolean>>()
        },
    ),
    "/studio/api/lowcode/model/page" to pathItem(
        post = operation("listModels", "Model") {
            jsonBody<ModelPageCommand>()
            jsonResponse<CommonResult<PageResult<ModelCommand>>>()
        },
    ),
    "/studio/api/lowcode/model/detail" to pathItem(
        get = operation("getModel", "Model") {
            longQueryParameter("id", required = true)
            jsonResponse<CommonResult<ModelCommand>>()
        },
    ),
    "/studio/api/lowcode/model/validate" to pathItem(
        post = operation("validateModel", "Model") {
            jsonBody<ModelCommand>()
            jsonResponse<CommonResult<MetadataValidationResult>>()
        },
    ),
    "/studio/api/lowcode/model/add" to pathItem(
        post = operation("addModel", "Model") {
            jsonBody<ModelCommand>()
            jsonResponse<CommonResult<Long>>()
        },
    ),
    "/studio/api/lowcode/model/update" to pathItem(
        put = operation("updateModel", "Model") {
            jsonBody<ModelCommand>()
            jsonResponse<CommonResult<Boolean>>()
        },
    ),
    "/studio/api/lowcode/model" to pathItem(
        delete = operation("deleteModels", "Model") {
            jsonBody<List<Long>>()
            jsonResponse<CommonResult<Boolean>>()
        },
    ),
    "/studio/api/lowcode/model/preview" to pathItem(
        get = operation("previewModel", "Model") {
            longQueryParameter("id", required = true)
            jsonResponse<CommonResult<ModelPreview>>()
        },
    ),
    "/studio/api/lowcode/model/download" to pathItem(
        get = operation("downloadModel", "Model") {
            longQueryParameter("id", required = true)
            binaryResponse()
        },
    ),
    "/studio/api/lowcode/dto/list" to pathItem(
        post = operation("listDtos", "DTO") {
            jsonResponse<CommonResult<List<DtoCommand>>>()
        },
    ),
    "/studio/api/lowcode/dto/detail" to pathItem(
        get = operation("getDto", "DTO") {
            longQueryParameter("id", required = true)
            jsonResponse<CommonResult<DtoCommand>>()
        },
    ),
    "/studio/api/lowcode/dto/validate" to pathItem(
        post = operation("validateDto", "DTO") {
            jsonBody<DtoCommand>()
            jsonResponse<CommonResult<MetadataValidationResult>>()
        },
    ),
    "/studio/api/lowcode/dto/add" to pathItem(
        post = operation("addDto", "DTO") {
            jsonBody<DtoCommand>()
            jsonResponse<CommonResult<Long>>()
        },
    ),
    "/studio/api/lowcode/dto/update" to pathItem(
        put = operation("updateDto", "DTO") {
            jsonBody<DtoCommand>()
            jsonResponse<CommonResult<Boolean>>()
        },
    ),
    "/studio/api/lowcode/dto" to pathItem(
        delete = operation("deleteDtos", "DTO") {
            jsonBody<List<Long>>()
            jsonResponse<CommonResult<Boolean>>()
        },
    ),
    "/studio/api/lowcode/dto/validation-rules" to pathItem(
        get = operation("listDtoValidationRules", "DTO") {
            jsonResponse<CommonResult<List<LsiValidationRuleMetadata>>>()
        },
    ),
    "/studio/api/lowcode/dto/reuse-analysis" to pathItem(
        post = operation("analyzeDtoReuse", "DTO") {
            jsonBody<DtoCommand>()
            jsonResponse<CommonResult<JsonElement>>()
        },
    ),
    "/studio/api/lowcode/dto/preview" to pathItem(
        get = operation("previewDto", "DTO") {
            longQueryParameter("id", required = true)
            jsonResponse<CommonResult<DtoPreview>>()
        },
    ),
    "/studio/api/lowcode/dto/download" to pathItem(
        get = operation("downloadDto", "DTO") {
            longQueryParameter("id", required = true)
            binaryResponse()
        },
    ),
    "/studio/api/lowcode/convention-file/list" to pathItem(
        post = operation("listConventionFiles", "Convention File") {
            jsonResponse<CommonResult<List<ConventionFileView>>>()
        },
    ),
    "/studio/api/lowcode/convention-file/detail" to pathItem(
        get = operation("getConventionFile", "Convention File") {
            longQueryParameter("id", required = true)
            jsonResponse<CommonResult<ConventionFileView>>()
        },
    ),
    "/studio/api/lowcode/convention-file/validate" to pathItem(
        post = operation("validateConventionFile", "Convention File") {
            jsonBody<ConventionFileCommand>()
            jsonResponse<CommonResult<MetadataValidationResult>>()
        },
    ),
    "/studio/api/lowcode/convention-file/add" to pathItem(
        post = operation("addConventionFile", "Convention File") {
            jsonBody<ConventionFileCommand>()
            jsonResponse<CommonResult<Long>>()
        },
    ),
    "/studio/api/lowcode/convention-file/update" to pathItem(
        put = operation("updateConventionFile", "Convention File") {
            jsonBody<ConventionFileCommand>()
            jsonResponse<CommonResult<Boolean>>()
        },
    ),
    "/studio/api/lowcode/convention-file" to pathItem(
        delete = operation("deleteConventionFiles", "Convention File") {
            jsonBody<List<Long>>()
            jsonResponse<CommonResult<Boolean>>()
        },
    ),
    "/studio/api/lowcode/constant/list" to pathItem(
        post = operation("listConstants", "Constant") {
            jsonBody<ConstantListCommand>()
            jsonResponse<CommonResult<List<ConstantView>>>()
        },
    ),
    "/studio/api/lowcode/constant/detail" to pathItem(
        get = operation("getConstant", "Constant") {
            longQueryParameter("id", required = true)
            jsonResponse<CommonResult<ConstantView>>()
        },
    ),
    "/studio/api/lowcode/constant/validate" to pathItem(
        post = operation("validateConstant", "Constant") {
            jsonBody<ConstantCommand>()
            jsonResponse<CommonResult<MetadataValidationResult>>()
        },
    ),
    "/studio/api/lowcode/constant/save" to pathItem(
        post = operation("saveConstant", "Constant") {
            jsonBody<ConstantCommand>()
            jsonResponse<CommonResult<ConstantView>>()
        },
    ),
    "/studio/api/lowcode/constant" to pathItem(
        delete = operation("deleteConstants", "Constant") {
            jsonBody<List<Long>>()
            jsonResponse<CommonResult<Boolean>>()
        },
    ),
)
