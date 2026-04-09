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
import java.io.File
import java.io.StringReader
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class AnnotationService(private val project: Project) {

    private val log = Logger.getInstance(AnnotationService::class.java)
    private val cache = ConcurrentHashMap<String, AnnotationData>()
    private var railsRoot: String? = null

    companion object {
        private val YAML_EXTENSIONS = listOf("yml", "yaml")

        @JvmStatic
        fun getInstance(project: Project): AnnotationService =
            project.getService(AnnotationService::class.java)
    }

    fun getAnnotationsDir(): String {
        val config = SidenotesSettings.getInstance(project)
        return config.annotationsDir
    }

    /**
     * Detects the Rails root by finding a directory containing both
     * .annotations/ and app/models/. Checks project root first, then
     * immediate subdirectories (monorepo support). Caches the result.
     */
    fun getRailsRoot(): String {
        railsRoot?.let { return it }

        val projectBase = project.basePath ?: return ""
        val config = SidenotesSettings.getInstance(project)

        // 1. Explicit config override
        if (config.railsRoot.isNotEmpty()) {
            val explicit = File(projectBase, config.railsRoot).absolutePath
            railsRoot = explicit
            return explicit
        }

        val annotationsDir = getAnnotationsDir()

        // 2. Check project root
        if (File(projectBase, annotationsDir).isDirectory &&
            File(projectBase, "app/models").isDirectory) {
            railsRoot = projectBase
            return projectBase
        }

        // 3. Scan immediate subdirectories
        val root = File(projectBase)
        root.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.forEach { dir ->
            if (File(dir, annotationsDir).isDirectory &&
                File(dir, "app/models").isDirectory) {
                railsRoot = dir.absolutePath
                return dir.absolutePath
            }
        }

        // Fallback to project root
        railsRoot = projectBase
        return projectBase
    }

    fun getAnnotationForModelFile(modelFile: VirtualFile): AnnotationData? {
        val railsRoot = getRailsRoot()
        val modelPath = modelFile.path

        val modelsPrefix = "$railsRoot/app/models/"
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
        railsRoot = null
        preloadAnnotations()
    }

    fun preloadAnnotations() {
        val railsRoot = getRailsRoot()
        val annotationsDir = LocalFileSystem.getInstance()
            .findFileByPath("$railsRoot/${getAnnotationsDir()}") ?: return

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
        val railsRoot = getRailsRoot()
        val annotationsDir = getAnnotationsDir()
        val fs = LocalFileSystem.getInstance()

        val file = YAML_EXTENSIONS.firstNotNullOfOrNull { ext ->
            fs.findFileByPath("$railsRoot/$annotationsDir/$modelName.$ext")
        } ?: return null

        return try {
            val content = String(file.contentsToByteArray())
            parseYaml(content)
        } catch (e: Exception) {
            log.warn("Failed to load annotation file: ${file.path}", e)
            null
        }
    }

    /**
     * Parses YAML in both the sidenotes gem format (wrapped under model name key
     * with metadata sub-key) and flat format (table_name/columns at root).
     */
    @Suppress("UNCHECKED_CAST")
    internal fun parseYaml(content: String): AnnotationData? {
        val yaml = Yaml(SafeConstructor(LoaderOptions()))
        val parsed: Map<String, Any> = yaml.load(StringReader(content)) ?: return null

        // Detect format: wrapped (ModelName → metadata + columns) vs flat (table_name at root)
        val root: Map<String, Any> = if (parsed.containsKey("table_name") || parsed.containsKey("columns")) {
            parsed
        } else {
            // Wrapped format: single top-level key is the model class name
            val keys = parsed.keys.toList()
            if (keys.size == 1 && parsed[keys[0]] is Map<*, *>) {
                parsed[keys[0]] as Map<String, Any>
            } else {
                parsed
            }
        }

        // Extract metadata sub-key if present, otherwise use root
        val metadata = (root["metadata"] as? Map<String, Any>) ?: root
        val tableName = metadata["table_name"] as? String ?: return null
        val primaryKey = metadata["primary_key"] as? String ?: "id"

        val enums = (metadata["enums"] as? Map<String, List<String>>) ?: emptyMap()
        val encryptedAttributes = (metadata["encrypted_attributes"] as? List<String>) ?: emptyList()

        val columns = (root["columns"] as? List<Map<String, Any>>)?.map { col ->
            ColumnInfo(
                name = col["name"] as? String ?: "",
                type = col["type"] as? String ?: "unknown",
                nullable = col["nullable"] as? Boolean ?: true,
                default = col["default"]?.toString(),
                limit = (col["limit"] as? Number)?.toInt(),
                precision = (col["precision"] as? Number)?.toInt(),
                scale = (col["scale"] as? Number)?.toInt(),
                comment = col["comment"] as? String
            )
        } ?: emptyList()

        val indexes = (root["indexes"] as? List<Map<String, Any>>)?.map { idx ->
            IndexInfo(
                name = idx["name"] as? String ?: "",
                columns = (idx["columns"] as? List<String>) ?: emptyList(),
                unique = idx["unique"] as? Boolean ?: false,
                using = idx["using"] as? String,
                where = idx["where"] as? String,
                comment = idx["comment"] as? String
            )
        } ?: emptyList()

        val associations = (root["associations"] as? List<Map<String, Any>>)?.map { assoc ->
            AssociationInfo(
                type = assoc["type"] as? String ?: "",
                name = assoc["name"] as? String ?: "",
                className = assoc["class_name"] as? String ?: "",
                foreignKey = assoc["foreign_key"] as? String ?: "",
                through = assoc["through"] as? String,
                polymorphic = assoc["polymorphic"] as? Boolean ?: false,
                dependent = assoc["dependent"] as? String
            )
        } ?: emptyList()

        return AnnotationData(
            tableName = tableName,
            primaryKey = primaryKey,
            columns = columns,
            indexes = indexes,
            associations = associations,
            enums = enums,
            encryptedAttributes = encryptedAttributes
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
