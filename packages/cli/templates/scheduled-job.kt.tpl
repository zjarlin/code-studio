{{header}}

{{documentation}}
@Single
class {{className}} : ScheduledJob {
    override val schedule: String = "0 0 0 * * * 480o"

    override suspend fun execute() = Unit
}
