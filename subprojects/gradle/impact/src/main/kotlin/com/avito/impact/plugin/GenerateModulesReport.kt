package com.avito.impact.plugin

import com.avito.impact.ModifiedProjectsFinder
import com.avito.impact.ConfigurationType
import com.avito.utils.createOrClear
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.Paths

/**
 * Prints impact analysis to files
 * FOR TESTING PURPOSES ONLY (assertion on gradle outputs)
 */
abstract class GenerateModulesReport : DefaultTask() {

    private val reportsDirectory = Paths.get(project.rootProject.buildDir.toString(), "reports", "modules").toFile()

    @OutputFile
    @Suppress("MemberVisibilityCanBePrivate")
    val implementationModulesReportFile = File(reportsDirectory, "implementation-modules.txt")

    @OutputFile
    @Suppress("MemberVisibilityCanBePrivate")
    val unitTestsModulesReportFile = File(reportsDirectory, "unit-tests-modules.txt")

    @OutputFile
    @Suppress("MemberVisibilityCanBePrivate")
    val androidTestsModulesReportFile = File(reportsDirectory, "android-tests-modules.txt")

    @TaskAction
    fun printReport() {
        val modifiedProjectsFinder = ModifiedProjectsFinder.from(project)

        printReport(implementationModulesReportFile, modifiedProjectsFinder.determineImpact(ConfigurationType.IMPLEMENTATION))
        printReport(unitTestsModulesReportFile, modifiedProjectsFinder.determineImpact(ConfigurationType.UNIT_TESTS))
        printReport(androidTestsModulesReportFile, modifiedProjectsFinder.determineImpact(ConfigurationType.ANDROID_TESTS))
    }

    private fun ModifiedProjectsFinder.determineImpact(configurationType: ConfigurationType): Set<Project> {
        return findModifiedProjects(configurationType).map { it.project }.toSet()
    }

    private fun printReport(
        report: File,
        modifiedProjects: Set<Project>
    ) {
        report.createOrClear()
        report.printWriter().use { writer ->
            modifiedProjects.forEach { project ->
                writer.println(project.path)
            }
        }
        logger.lifecycle("Wrote changed modules' report file://${report.path}")
    }
}
