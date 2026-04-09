package com.sidenotes.actions

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.sidenotes.SidenotesBundle
import com.sidenotes.services.AnnotationService

class RegenerateAnnotationsAction : AnAction() {

    private val log = Logger.getInstance(RegenerateAnnotationsAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            SidenotesBundle.message("action.regenerate.progress"),
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = SidenotesBundle.message("action.regenerate.progress")
                runGenerate(project)
            }
        })
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    private fun runGenerate(project: Project) {
        val projectPath = project.basePath ?: return

        val commandLine = GeneralCommandLine("bundle", "exec", "rake", "sidenotes:generate")
            .withWorkDirectory(projectPath)

        try {
            val handler = OSProcessHandler(commandLine)
            val output = StringBuilder()
            val errors = StringBuilder()

            handler.addProcessListener(object : ProcessAdapter() {
                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                    if (outputType === ProcessOutputTypes.STDERR) {
                        errors.append(event.text)
                    } else {
                        output.append(event.text)
                    }
                }
            })

            handler.startNotify()
            handler.waitFor()

            val exitCode = handler.exitCode ?: -1

            if (exitCode == 0) {
                AnnotationService.getInstance(project).invalidateAll()
                notify(
                    project,
                    SidenotesBundle.message("action.regenerate.success"),
                    NotificationType.INFORMATION
                )
                log.info("Sidenotes annotations regenerated successfully")
            } else {
                val errorMsg = errors.toString().take(500)
                notify(
                    project,
                    "${SidenotesBundle.message("action.regenerate.failure")}\n$errorMsg",
                    NotificationType.ERROR
                )
                log.warn("Sidenotes generation failed with exit code $exitCode: $errorMsg")
            }
        } catch (e: Exception) {
            notify(
                project,
                "${SidenotesBundle.message("action.regenerate.failure")}\n${e.message}",
                NotificationType.ERROR
            )
            log.error("Failed to run sidenotes:generate", e)
        }
    }

    private fun notify(project: Project, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Sidenotes Notifications")
            .createNotification(content, type)
            .notify(project)
    }
}
