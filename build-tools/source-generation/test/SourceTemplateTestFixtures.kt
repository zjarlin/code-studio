package site.addzero.toolchain.lowcode

import site.addzero.platform.lowcode.generator.SourceTemplateCatalog
import site.addzero.platform.lowcode.generator.SourceTemplateKind
import java.nio.file.Files
import java.nio.file.Path

internal fun writeSourceTemplates(directory: Path): Path {
    Files.createDirectories(directory)
    SourceTemplateKind.entries.forEach { kind ->
        Files.writeString(directory.resolve(kind.fileName), SourceTemplateCatalog.DEFAULT.source(kind))
    }
    return directory
}
