package site.addzero.studio.workbench

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import site.addzero.studio.workbench.transport.StudioProviders

@Module(includes = [StudioProviders::class])
@ComponentScan("site.addzero.studio.workbench")
class StudioUiModule
