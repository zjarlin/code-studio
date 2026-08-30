package site.addzero.studio.workbench.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.koin.core.annotation.Single
import site.addzero.studio.contract.ConstantCommand
import site.addzero.studio.contract.ConventionFileCommand
import site.addzero.studio.contract.ConventionFileView
import site.addzero.studio.contract.DtoCommand
import site.addzero.studio.contract.LibraryCommand
import site.addzero.studio.contract.LibraryFeatureCommand
import site.addzero.studio.contract.LibraryFeatureView
import site.addzero.studio.contract.LibraryView
import site.addzero.studio.contract.MetadataValidationResult
import site.addzero.studio.contract.ModelCommand
import site.addzero.studio.contract.ModelPageCommand
import site.addzero.studio.contract.PreviewFile
import site.addzero.studio.workbench.transport.StudioApi
import site.addzero.studio.workbench.transport.StudioSessionState

enum class LibraryResourceKind {
    LIBRARY,
    FEATURE,
    MODEL,
    DTO,
    CONSTANT,
    CONVENTION_FILE,
}

data class ResourceSelection(
    val kind: LibraryResourceKind,
    val id: Long?,
)

@Single
class LibraryWorkspaceState(
    private val api: StudioApi,
    private val session: StudioSessionState,
) {
    var libraries by mutableStateOf<List<LibraryView>>(emptyList())
        private set
    var features by mutableStateOf<List<LibraryFeatureView>>(emptyList())
        private set
    var models by mutableStateOf<List<ModelCommand>>(emptyList())
        private set
    var dtos by mutableStateOf<List<DtoCommand>>(emptyList())
        private set
    var constants by mutableStateOf<List<ConstantCommand>>(emptyList())
        private set
    var conventionFiles by mutableStateOf<List<ConventionFileView>>(emptyList())
        private set
    var selectedLibraryId by mutableStateOf<Long?>(null)
        private set
    var selectedFeatureId by mutableStateOf<Long?>(null)
        private set
    var selection by mutableStateOf(ResourceSelection(LibraryResourceKind.LIBRARY, null))
        private set
    var pendingSelection by mutableStateOf<ResourceSelection?>(null)
        private set
    var libraryDraft by mutableStateOf<LibraryCommand?>(null)
        private set
    var featureDraft by mutableStateOf<LibraryFeatureCommand?>(null)
        private set
    var modelDraft by mutableStateOf<ModelCommand?>(null)
        private set
    var dtoDraft by mutableStateOf<DtoCommand?>(null)
        private set
    var constantDraft by mutableStateOf<ConstantCommand?>(null)
        private set
    var conventionFileDraft by mutableStateOf<ConventionFileCommand?>(null)
        private set
    var previewFiles by mutableStateOf<List<PreviewFile>>(emptyList())
        private set
    var validation by mutableStateOf<MetadataValidationResult?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    var dirty by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    suspend fun load() = runLoading {
        libraries = api.libraries()
        val libraryId = selectedLibraryId ?: libraries.firstOrNull()?.id
        if (libraryId != null) {
            selectLibrary(libraryId)
        }
    }

    suspend fun selectLibrary(id: Long) = runLoading {
        selectedLibraryId = id
        features = api.libraryFeatures(id)
        selectedFeatureId = selectedFeatureId?.takeIf { selected -> features.any { it.id == selected } }
            ?: features.firstOrNull()?.id
        refreshFeatureResources()
        val library = libraries.first { it.id == id }
        libraryDraft = library.toCommand()
        selection = ResourceSelection(LibraryResourceKind.LIBRARY, id)
        dirty = false
    }

    suspend fun selectFeature(id: Long) = runLoading {
        selectedFeatureId = id
        refreshFeatureResources()
        val feature = features.first { it.id == id }
        featureDraft = LibraryFeatureCommand(
            id = feature.id,
            libraryId = feature.libraryId,
            parentId = feature.parentId,
            featureCode = feature.featureCode,
            name = feature.name,
            description = feature.description,
        )
        selection = ResourceSelection(LibraryResourceKind.FEATURE, id)
        dirty = false
    }

    fun requestSelection(value: ResourceSelection): Boolean {
        if (dirty && value != selection) {
            pendingSelection = value
            return false
        }
        applySelection(value)
        return true
    }

    fun discardAndSelect() {
        val target = pendingSelection ?: return
        dirty = false
        pendingSelection = null
        applySelection(target)
    }

    fun keepEditing() {
        pendingSelection = null
    }

    fun newResource(kind: LibraryResourceKind) {
        val featureId = selectedFeatureId
        selection = ResourceSelection(kind, null)
        validation = null
        previewFiles = emptyList()
        when (kind) {
            LibraryResourceKind.LIBRARY -> libraryDraft = newLibraryDraft()
            LibraryResourceKind.FEATURE -> featureDraft = selectedLibraryId?.let { libraryId ->
                LibraryFeatureCommand(
                    libraryId = libraryId,
                    featureCode = "",
                    name = "",
                )
            }
            LibraryResourceKind.MODEL -> modelDraft = featureId?.let(::newModelDraft)
            LibraryResourceKind.DTO -> dtoDraft = featureId?.let(::newDtoDraft)
            LibraryResourceKind.CONSTANT -> constantDraft = featureId?.let(::newConstantDraft)
            LibraryResourceKind.CONVENTION_FILE -> conventionFileDraft = featureId?.let(::newConventionFileDraft)
        }
        dirty = true
    }

    fun editLibrary(block: (LibraryCommand) -> LibraryCommand) = edit(libraryDraft, block) { libraryDraft = it }
    fun editFeature(block: (LibraryFeatureCommand) -> LibraryFeatureCommand) = edit(featureDraft, block) { featureDraft = it }
    fun editModel(block: (ModelCommand) -> ModelCommand) = edit(modelDraft, block) { modelDraft = it }
    fun editDto(block: (DtoCommand) -> DtoCommand) = edit(dtoDraft, block) { dtoDraft = it }
    fun editConstant(block: (ConstantCommand) -> ConstantCommand) = edit(constantDraft, block) { constantDraft = it }
    fun editConventionFile(block: (ConventionFileCommand) -> ConventionFileCommand) =
        edit(conventionFileDraft, block) { conventionFileDraft = it }

    suspend fun save() = runSaving {
        validation = when (selection.kind) {
            LibraryResourceKind.LIBRARY -> libraryDraft?.let { command ->
                api.validateLibrary(command).also { result -> if (result.valid) api.saveLibrary(command) }
            }
            LibraryResourceKind.FEATURE -> featureDraft?.let { command ->
                api.validateLibraryFeature(command).also { result -> if (result.valid) api.saveLibraryFeature(command) }
            }
            LibraryResourceKind.MODEL -> modelDraft?.let { command ->
                api.validateModel(command).also { result -> if (result.valid) api.saveModel(command) }
            }
            LibraryResourceKind.DTO -> dtoDraft?.let { command ->
                api.validateDto(command).also { result -> if (result.valid) api.saveDto(command) }
            }
            LibraryResourceKind.CONSTANT -> constantDraft?.let { command ->
                api.validateConstant(command).also { result -> if (result.valid) api.saveConstant(command) }
            }
            LibraryResourceKind.CONVENTION_FILE -> conventionFileDraft?.let { command ->
                api.validateConventionFile(command).also { result -> if (result.valid) api.saveConventionFile(command) }
            }
        }
        if (validation?.valid == true) {
            dirty = false
            load()
        }
    }

    suspend fun deleteSelected() = runSaving {
        val id = selection.id ?: return@runSaving
        when (selection.kind) {
            LibraryResourceKind.LIBRARY -> api.deleteLibrary(id)
            LibraryResourceKind.FEATURE -> api.deleteLibraryFeature(id)
            LibraryResourceKind.MODEL -> api.deleteModel(id)
            LibraryResourceKind.DTO -> api.deleteDto(id)
            LibraryResourceKind.CONSTANT -> api.deleteConstant(id)
            LibraryResourceKind.CONVENTION_FILE -> api.deleteConventionFile(id)
        }
        selection = ResourceSelection(LibraryResourceKind.LIBRARY, selectedLibraryId)
        dirty = false
        load()
    }

    suspend fun preview() = runLoading {
        val id = selection.id ?: return@runLoading
        previewFiles = when (selection.kind) {
            LibraryResourceKind.LIBRARY -> api.previewLibrary(id, selectedFeatureId).files
            LibraryResourceKind.FEATURE -> api.previewLibrary(selectedLibraryId ?: return@runLoading, id).files
            LibraryResourceKind.MODEL -> api.previewModel(id).files
            LibraryResourceKind.DTO -> api.previewDto(id).files
            LibraryResourceKind.CONSTANT,
            LibraryResourceKind.CONVENTION_FILE,
            -> emptyList()
        }
    }

    fun closePreview() {
        previewFiles = emptyList()
    }

    private suspend fun refreshFeatureResources() {
        val featureId = selectedFeatureId
        models = api.modelPage(ModelPageCommand(pageSize = 1000)).rows.filter { it.featureId == featureId }
        dtos = api.dtos().filter { it.featureId == featureId }
        constants = api.constants(featureId)
        conventionFiles = api.conventionFiles().filter { it.featureId == featureId }
    }

    private fun applySelection(value: ResourceSelection) {
        selection = value
        validation = null
        previewFiles = emptyList()
        when (value.kind) {
            LibraryResourceKind.LIBRARY -> libraryDraft = libraries.firstOrNull { it.id == value.id }?.toCommand()
            LibraryResourceKind.FEATURE -> featureDraft = features.firstOrNull { it.id == value.id }?.let { feature ->
                LibraryFeatureCommand(
                    id = feature.id,
                    libraryId = feature.libraryId,
                    parentId = feature.parentId,
                    featureCode = feature.featureCode,
                    name = feature.name,
                    description = feature.description,
                )
            }
            LibraryResourceKind.MODEL -> modelDraft = models.firstOrNull { it.id == value.id }
            LibraryResourceKind.DTO -> dtoDraft = dtos.firstOrNull { it.id == value.id }
            LibraryResourceKind.CONSTANT -> constantDraft = constants.firstOrNull { it.id == value.id }
            LibraryResourceKind.CONVENTION_FILE -> conventionFileDraft =
                conventionFiles.firstOrNull { it.id == value.id }?.toCommand()
        }
        dirty = false
    }

    private inline fun <T> edit(value: T?, block: (T) -> T, update: (T) -> Unit) {
        value ?: return
        update(block(value))
        validation = null
        dirty = true
    }

    private suspend fun runLoading(block: suspend () -> Unit) {
        loading = true
        error = null
        try {
            block()
        } catch (cause: Throwable) {
            error = cause.message ?: "读取 Studio 元数据失败"
        } finally {
            loading = false
        }
    }

    private suspend fun runSaving(block: suspend () -> Unit) {
        saving = true
        error = null
        try {
            block()
        } catch (cause: Throwable) {
            error = cause.message ?: "保存 Studio 元数据失败"
        } finally {
            saving = false
        }
    }

    private fun LibraryView.toCommand() = LibraryCommand(id, code, displayName, version, status, spec)

    private fun ConventionFileView.toCommand() = ConventionFileCommand(
        id = id,
        featureId = featureId,
        fileCode = fileCode,
        name = name,
        className = className,
        kind = kind,
        status = status,
        description = description,
    )

    private fun newLibraryDraft(): LibraryCommand {
        val contributorId = session.config?.editableContributorId.orEmpty()
        return LibraryCommand(
            code = contributorId,
            displayName = "",
            spec = site.addzero.studio.contract.LibrarySpec(
                contributorId = contributorId,
                packagePrefix = "",
                scanPackage = "",
            ),
        )
    }
}
