package com.sidenotes.services

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent

class AnnotationFileWatcher : ProjectActivity {

    private val log = Logger.getInstance(AnnotationFileWatcher::class.java)

    override suspend fun execute(project: Project) {
        val service = AnnotationService.getInstance(project)
        service.preloadAnnotations()

        project.messageBus.connect().subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: MutableList<out VFileEvent>) {
                    val railsRoot = service.getRailsRoot()
                    val annotationsDir = service.getAnnotationsDir()
                    val annotationsPrefix = "$railsRoot/$annotationsDir/"

                    for (event in events) {
                        val path = event.path ?: continue
                        if (!path.startsWith(annotationsPrefix)) continue
                        if (!path.endsWith(".yml") && !path.endsWith(".yaml")) continue

                        val relativePath = path
                            .removePrefix(annotationsPrefix)
                            .removeSuffix(".yml")
                            .removeSuffix(".yaml")

                        when (event) {
                            is VFileContentChangeEvent -> {
                                log.info("Annotation file changed: $relativePath")
                                service.invalidate(relativePath)
                                service.getAnnotation(relativePath)
                            }
                            is VFileCreateEvent -> {
                                log.info("Annotation file created: $relativePath")
                                service.getAnnotation(relativePath)
                            }
                            is VFileDeleteEvent -> {
                                log.info("Annotation file deleted: $relativePath")
                                service.invalidate(relativePath)
                            }
                        }
                    }
                }
            }
        )

        log.info("Sidenotes file watcher initialized for project: ${project.name}")
    }
}
