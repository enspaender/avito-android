package com.avito.instrumentation.configuration

import com.avito.android.withAndroidModule
import com.avito.git.gitState
import com.avito.instrumentation.configuration.InstrumentationFilter.FromRunHistory.RunStatus
import com.avito.report.model.RunId
import com.avito.utils.gradle.envArgs
import com.avito.utils.logging.ciLogger
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType

internal fun Project.createInstrumentationPluginExtension() {
    val extension =
        extensions.create<InstrumentationPluginConfiguration.GradleInstrumentationPluginConfiguration>(
            "instrumentation",
            this
        )
    extension.filters.register("default") {
        it.fromRunHistory.excludePreviousStatuses(setOf(RunStatus.Manual, RunStatus.Success))
    }
}

/**
 * Нужна промежуточная модель т.к светить везде gradle api не очень хорошо.
 * Лишние методы (для dsl) загрязняют скоуп и, что более важное, могут аффектить
 * поведение. Например, наличие динамической конфигурации с помощью NamedDomainObjectContainer
 * мешает нормально сериализации объекта для дальнейшей передачи в воркеры.
 */
internal fun Project.withInstrumentationExtensionData(action: (InstrumentationPluginConfiguration.GradleInstrumentationPluginConfiguration.Data) -> Unit) {
    withAndroidModule { androidBaseExtension ->
        afterEvaluate {
            val extension = extensions.getByType<InstrumentationPluginConfiguration.GradleInstrumentationPluginConfiguration>()
            val env = project.envArgs
            val runId = project.gitState { project.ciLogger.info(it) }
                .map { gitState ->
                    RunId(
                        commitHash = gitState.currentBranch.commit,
                        buildTypeId = env.build.type
                    )
                }.orNull

            val runIdOverride = runId?.let {
                mapOf("runId" to it.toString())
            } ?: emptyMap()

            val instrumentationParameters = InstrumentationParameters()
                .applyParameters(androidBaseExtension.defaultConfig.testInstrumentationRunnerArguments)
                .applyParameters(runIdOverride)
                .applyParameters(
                    mapOf(
                        "teamcityBuildId" to envArgs.build.id.toString()
                    )
                )

            action(
                extension.toData(instrumentationParameters)
            )
        }
    }
}
