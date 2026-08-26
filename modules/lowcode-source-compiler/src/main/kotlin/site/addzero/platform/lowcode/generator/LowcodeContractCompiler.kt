package site.addzero.platform.lowcode.generator

/** 编译独立契约源码，并补全契约引用的 DTO 结构。 */
object LowcodeContractCompiler {
    fun compile(contract: LsiLowcodeContract): List<LowcodeGeneratedFile> =
        LowcodeDomainServiceSourceGenerator.generate(contract, LowcodeDtoTypeCatalog.EMPTY)

    fun compile(
        contract: LsiLowcodeContract,
        models: Collection<LowcodeModelMeta>,
        dtoDefinitions: Collection<LsiLowcodeDtoDefinition> = emptyList(),
    ): List<LowcodeGeneratedFile> {
        val resolvedContract = resolve(contract, models, dtoDefinitions)
        val dtoCatalog = LowcodeDtoTypeCatalog.from(models, dtoDefinitions)
        return LowcodeDomainServiceSourceGenerator.generate(resolvedContract, dtoCatalog)
    }

    fun resolve(
        contract: LsiLowcodeContract,
        models: Collection<LowcodeModelMeta>,
        dtoDefinitions: Collection<LsiLowcodeDtoDefinition> = emptyList(),
    ): LsiLowcodeContract {
        return contract.copy(
            dtoSchemas = contract.operations.resolveReferencedDtoSchemas(
                models = models,
                dtoDefinitions = dtoDefinitions,
                existingSchemas = contract.dtoSchemas,
            ),
        )
    }
}
