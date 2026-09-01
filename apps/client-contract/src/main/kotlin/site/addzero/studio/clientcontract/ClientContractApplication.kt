package site.addzero.studio.clientcontract

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

fun main(arguments: Array<String>) {
    require(arguments.size == 1) {
        "用法: client-contract <openapi-output>"
    }
    val output = Path.of(arguments.single()).toAbsolutePath().normalize()
    output.parent?.let(Files::createDirectories)
    Files.writeString(output, ConsoleClientContract.generate(), StandardCharsets.UTF_8)
}
