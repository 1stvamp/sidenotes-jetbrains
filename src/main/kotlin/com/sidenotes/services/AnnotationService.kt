package com.sidenotes.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.sidenotes.models.*
import org.yaml.snakeyaml.Yaml
import java.io.StringReader
import java.util.concurrent.ConcurrentHashMap

/**
 * Project-level service that manages loading, caching, and querying
 * annotation data from .annotations/*.yml files.
 */
@Service(Service.Level.PROJECT)
class AnnotationService(private val project: Project) {

    private val log = Logger.getInstance(AnnotationService::class.java)
    private val cache = ConcurrentHashMap<String, AnnotationData>()

    companion object {
        private const val ANNOTATIONS_DIR = ".annotations"

        @JvmStatic
        fun getInstance(project: Project): AnnotationService =
            project.getService(AnnotationService::class.java)
    }

    /**
     * Resolves a model file path to its annotation file.
     * Supports namespaced models: app/models/admin/user.rb → .annotations/admin/user.yml
     */
    fun getAnnotationForModelFile(modelFile: VirtualFile): AnnotationData? {
        val projectBase = project.basePath ?: return null
        val modelPath = modelFile.path

        // Extract relative path from app/models/
        val modelsPrefix = "$projectBase/app/models/"
        if (!modelPath.startsWith(modelsPrefix)) return null

        val relativePath = modelPath.removePrefix(modelsPrefix)
        val annotationName = relativePath.removeSuffix(".rb")

        return getAnnotation(annotationName)
    }

    /**
     * Gets annotation data by model name (e.g., "user" or "admin/user").
     * Loads from cache or parses from file.
     */
    fun getAnnotation(modelName: String): AnnotationData? {
        cache[modelName]?.let { return it }

        val data = loadAnnotationFile(modelName) ?: return null
        cache[modelName] = data
        return data
    }

    /**
     * Returns all currently cached annotations.
     */
    fun getAllAnnotations(): Map<String, AnnotationData> = cache.toMap()

    /**
     * Invalidates the cache for a specific model name.
     */
    fun invalidate(modelName: String) {
        cache.remove(modelName)
    }

    /**
     * Clears the entire annotation cache and reloads from disk.
     */
    fun invalidateAll() {
        cache.clear()
        preloadAnnotations()
    }

    /**
     * Scans the .annotations directory and preloads all YAML files.
     */
    fun preloadAnnotations() {
        val projectBase = project.basePath ?: return
        val annotationsDir = LocalFileSystem.getInstance()
            .findFileByPath("$projectBase/$ANNOTATIONS_DIR") ?: return

        loadAnnotationsRecursive(annotationsDir, "")
    }

    private fun loadAnnotationsRecursive(dir: VirtualFile, prefix: String) {
        for (child in dir.children) {
            if (child.isDirectory) {
                val newPrefix = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
                loadAnnotationsRecursive(child, newPrefix)
            } else if (child.extension == "yml" || child.extension == "yaml") {
                val modelName = if (prefix.isEmpty()) {
                    child.nameWithoutExtension
                } else {
                    "$prefix/${child.nameWithoutExtension}"
                }
                try {
                    val data = parseYaml(String(child.contentsToByteArray()))
                    if (data != null) {
                        cache[modelName] = data
                    }
                } catch (e: Exception) {
                    log.warn("Failed to parse annotation file: ${child.path}", e)
                }
            }
        }
    }

    private fun loadAnnotationFile(modelName: String): AnnotationData? {
        val projectBase = project.basePath ?: return null
        val filePath = "$projectBase/$ANNOTATIONS_DIR/$modelName.yml"

        val file = LocalFileSystem.getInstance().findFileByPath(filePath) ?: return null

        return try {
            val content = String(file.contentsToByteArray())
            parseYaml(content)
        } catch (e: Exception) {
            log.warn("Failed to load annotation file: $filePath", e)
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    internal fun parseYaml(content: String): AnnotationData? {
        val yaml = Yaml()
        val data: Map<String, Any> = yaml.load(StringReader(content)) ?: return null

        val tableName = data["table_name"] as? String ?: return null
        val primaryKey = data["primary_key"] as? String ?: "id"

        val columns = (data["columns"] as? List<Map<String, Any>>)?.map { col ->
            ColumnInfo(
                name = col["name"] as? String ?: "",
                type = col["type"] as? String ?: "unknown",
                nullable = col["nullable"] as? Boolean ?: true,
                default = col["default"]?.toString()
            )
        } ?: emptyList()

        val indexes = (data["indexes"] as? List<Map<String, Any>>)?.map { idx ->
            IndexInfo(
                name = idx["name"] as? String ?: "",
                columns = (idx["columns"] as? List<String>) ?: emptyList(),
                unique = idx["unique"] as? Boolean ?: false
            )
        } ?: emptyList()

        val associations = (data["associations"] as? List<Map<String, Any>>)?.map { assoc ->
            AssociationInfo(
                type = assoc["type"] as? String ?: "",
                name = assoc["name"] as? String ?: "",
                className = assoc["class_name"] as? String ?: "",
                foreignKey = assoc["foreign_key"] as? String ?: ""
            )
        } ?: emptyList()

        return AnnotationData(
            tableName = tableName,
            primaryKey = primaryKey,
            columns = columns,
            indexes = indexes,
            associations = associations
        )
    }

    /**
     * Resolves a model class name (e.g., "User" or "Admin::User") to its annotation.
     */
    fun getAnnotationForClassName(className: String): AnnotationData? {
        // Convert CamelCase class name to snake_case file name
        // Admin::User → admin/user
        val modelName = className
            .replace("::", "/")
            .replace(Regex("([a-z])([A-Z])")) { "${it.groupValues[1]}_${it.groupValues[2]}" }
            .lowercase()

        return getAnnotation(modelName)
    }
}
