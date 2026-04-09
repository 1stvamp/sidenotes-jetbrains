package com.sidenotes.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.sidenotes.models.*
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.io.StringReader
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class AnnotationService(private val project: Project) {

    private val log = Logger.getInstance(AnnotationService::class.java)
    private val cache = ConcurrentHashMap<String, AnnotationData>()

    companion object {
        private const val ANNOTATIONS_DIR = ".annotations"
        private val YAML_EXTENSIONS = listOf("yml", "yaml")

        @JvmStatic
        fun getInstance(project: Project): AnnotationService =
            project.getService(AnnotationService::class.java)
    }

    /**
     * Resolves a model file path to its annotation.
     * Supports namespaced models: app/models/admin/user.rb -> .annotations/admin/user.yml
     */
    fun getAnnotationForModelFile(modelFile: VirtualFile): AnnotationData? {
        val projectBase = project.basePath ?: return null
        val modelPath = modelFile.path

        val modelsPrefix = "$projectBase/app/models/"
        if (!modelPath.startsWith(modelsPrefix)) return null

        val relativePath = modelPath.removePrefix(modelsPrefix)
        val annotationName = relativePath.removeSuffix(".rb")

        return getAnnotation(annotationName)
    }

    fun getAnnotation(modelName: String): AnnotationData? {
        cache[modelName]?.let { return it }

        val data = loadAnnotationFile(modelName) ?: return null
        cache[modelName] = data
        return data
    }

    fun getAllAnnotations(): Map<String, AnnotationData> = cache.toMap()

    fun invalidate(modelName: String) {
        cache.remove(modelName)
    }

    fun invalidateAll() {
        cache.clear()
        preloadAnnotations()
    }

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
            } else if (child.extension in YAML_EXTENSIONS) {
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
        val fs = LocalFileSystem.getInstance()

        // Try both .yml and .yaml extensions
        val file = YAML_EXTENSIONS.firstNotNullOfOrNull { ext ->
            fs.findFileByPath("$projectBase/$ANNOTATIONS_DIR/$modelName.$ext")
        } ?: return null

        return try {
            val content = String(file.contentsToByteArray())
            parseYaml(content)
        } catch (e: Exception) {
            log.warn("Failed to load annotation file: ${file.path}", e)
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    internal fun parseYaml(content: String): AnnotationData? {
        // SafeConstructor restricts deserialization to basic types only, preventing RCE
        val yaml = Yaml(SafeConstructor(LoaderOptions()))
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
     * Converts a CamelCase class name to a snake_case model path.
     * Handles acronyms: "Admin::User" -> "admin/user", "APIKey" -> "api_key"
     */
    fun getAnnotationForClassName(className: String): AnnotationData? {
        val modelName = className
            .replace("::", "/")
            .replace(Regex("([A-Z]+)([A-Z][a-z])")) { "${it.groupValues[1]}_${it.groupValues[2]}" }
            .replace(Regex("([a-z\\d])([A-Z])")) { "${it.groupValues[1]}_${it.groupValues[2]}" }
            .lowercase()

        return getAnnotation(modelName)
    }
}
