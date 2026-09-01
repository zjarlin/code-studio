package site.addzero.studio.contract

import kotlinx.serialization.Serializable

@Serializable
enum class ApplicationIdentityMode { EXTERNAL_JWT, LOCAL }

@Serializable
enum class LibraryKind { BUSINESS, BUILT_IN }

@Serializable
data class LibraryDataScopeDescriptor(
    val tenantScoped: Boolean = false,
    val userScoped: Boolean = false,
    val departmentScoped: Boolean = false,
)

@Serializable
data class LibrarySpec(
    val schemaVersion: Int = 1,
    val description: String? = null,
    val contributorId: String,
    val packagePrefix: String,
    val scanPackage: String,
    val kind: LibraryKind = LibraryKind.BUSINESS,
    val runtimeDependencies: List<String> = emptyList(),
    val supportedIdentityModes: List<ApplicationIdentityMode> = emptyList(),
    val applicationSelectable: Boolean = true,
    val dataScope: LibraryDataScopeDescriptor = LibraryDataScopeDescriptor(),
)

@Serializable
data class LibraryCommand(
    val id: Long? = null,
    val code: String,
    val displayName: String,
    val version: Int = 1,
    val status: Int = 1,
    val spec: LibrarySpec,
)

@Serializable
data class LibraryFeatureCommand(
    val id: Long? = null,
    val libraryId: Long,
    val parentId: Long? = null,
    val featureCode: String,
    val name: String,
    val description: String? = null,
)

@Serializable
data class LibraryFeatureView(
    val id: Long,
    val libraryId: Long,
    val parentId: Long? = null,
    val featureCode: String,
    val name: String,
    val description: String? = null,
)

@Serializable
data class LibraryView(
    val id: Long,
    val code: String,
    val displayName: String,
    val version: Int,
    val status: Int,
    val spec: LibrarySpec,
    val features: List<LibraryFeatureView> = emptyList(),
)

@Serializable
data class LibraryPage(
    val list: List<LibraryView> = emptyList(),
    val total: Long = 0,
)
