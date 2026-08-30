package site.addzero.studio.web

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module
import site.addzero.studio.workbench.StudioUiModule

@KoinApplication(modules = [StudioWebModule::class])
class StudioWebApplication

@Module(includes = [StudioUiModule::class])
@ComponentScan("site.addzero.studio.web")
class StudioWebModule
