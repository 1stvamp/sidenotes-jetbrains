package com.sidenotes.models

private fun String.escapeHtml(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

data class AnnotationData(
    val tableName: String,
    val primaryKey: String,
    val columns: List<ColumnInfo>,
    val indexes: List<IndexInfo>,
    val associations: List<AssociationInfo>,
    val enums: Map<String, List<String>> = emptyMap(),
    val encryptedAttributes: List<String> = emptyList()
) {
    fun toSummaryText(): String {
        val colCount = columns.size
        val idxCount = indexes.size
        val assocCount = associations.size
        return "Table: $tableName | $colCount columns, $idxCount indexes, $assocCount associations"
    }

    fun toHtmlDocumentation(): String = buildString {
        append("<div style='font-family: monospace;'>")

        append("<h2>Table: <code>${tableName.escapeHtml()}</code></h2>")
        append("<p>Primary key: <code>${primaryKey.escapeHtml()}</code></p>")

        append("<h3>Columns</h3>")
        append("<table style='border-collapse: collapse; width: 100%;'>")
        append("<tr>")
        append("<th style='padding: 4px 8px; text-align: left; border-bottom: 1px solid gray;'>Name</th>")
        append("<th style='padding: 4px 8px; text-align: left; border-bottom: 1px solid gray;'>Type</th>")
        append("<th style='padding: 4px 8px; text-align: left; border-bottom: 1px solid gray;'>Nullable</th>")
        append("<th style='padding: 4px 8px; text-align: left; border-bottom: 1px solid gray;'>Default</th>")
        append("<th style='padding: 4px 8px; text-align: left; border-bottom: 1px solid gray;'>Limit</th>")
        append("</tr>")
        for (col in columns) {
            append("<tr>")
            append("<td style='padding: 3px 8px;'><code>${col.name.escapeHtml()}</code></td>")
            append("<td style='padding: 3px 8px;'>${col.type.escapeHtml()}</td>")
            append("<td style='padding: 3px 8px;'>${if (col.nullable) "yes" else "no"}</td>")
            append("<td style='padding: 3px 8px;'>${col.default?.escapeHtml() ?: ""}</td>")
            val limit = col.limitDescription()
            append("<td style='padding: 3px 8px;'>${limit.escapeHtml()}</td>")
            append("</tr>")
        }
        append("</table>")

        if (indexes.isNotEmpty()) {
            append("<h3>Indexes</h3>")
            append("<table style='border-collapse: collapse; width: 100%;'>")
            append("<tr>")
            append("<th style='padding: 4px 8px; text-align: left; border-bottom: 1px solid gray;'>Name</th>")
            append("<th style='padding: 4px 8px; text-align: left; border-bottom: 1px solid gray;'>Columns</th>")
            append("<th style='padding: 4px 8px; text-align: left; border-bottom: 1px solid gray;'>Unique</th>")
            append("<th style='padding: 4px 8px; text-align: left; border-bottom: 1px solid gray;'>Using</th>")
            append("</tr>")
            for (idx in indexes) {
                append("<tr>")
                append("<td style='padding: 3px 8px;'><code>${idx.name.escapeHtml()}</code></td>")
                append("<td style='padding: 3px 8px;'>${idx.columns.joinToString(", ") { it.escapeHtml() }}</td>")
                append("<td style='padding: 3px 8px;'>${if (idx.unique) "✓" else ""}</td>")
                append("<td style='padding: 3px 8px;'>${idx.using?.escapeHtml() ?: ""}</td>")
                append("</tr>")
            }
            append("</table>")
        }

        if (associations.isNotEmpty()) {
            append("<h3>Associations</h3>")
            append("<table style='border-collapse: collapse; width: 100%;'>")
            append("<tr>")
            append("<th style='padding: 4px 8px; text-align: left; border-bottom: 1px solid gray;'>Type</th>")
            append("<th style='padding: 4px 8px; text-align: left; border-bottom: 1px solid gray;'>Name</th>")
            append("<th style='padding: 4px 8px; text-align: left; border-bottom: 1px solid gray;'>Class</th>")
            append("<th style='padding: 4px 8px; text-align: left; border-bottom: 1px solid gray;'>Foreign Key</th>")
            append("<th style='padding: 4px 8px; text-align: left; border-bottom: 1px solid gray;'>Options</th>")
            append("</tr>")
            for (assoc in associations) {
                val options = buildList {
                    if (assoc.through != null) add("through: ${assoc.through}")
                    if (assoc.polymorphic) add("polymorphic")
                    if (assoc.dependent != null) add("dependent: ${assoc.dependent}")
                }.joinToString(", ")
                append("<tr>")
                append("<td style='padding: 3px 8px;'>${assoc.type.escapeHtml()}</td>")
                append("<td style='padding: 3px 8px;'><code>${assoc.name.escapeHtml()}</code></td>")
                append("<td style='padding: 3px 8px;'>${assoc.className.escapeHtml()}</td>")
                append("<td style='padding: 3px 8px;'>${assoc.foreignKey.escapeHtml()}</td>")
                append("<td style='padding: 3px 8px;'>${options.escapeHtml()}</td>")
                append("</tr>")
            }
            append("</table>")
        }

        if (enums.isNotEmpty()) {
            append("<h3>Enums</h3>")
            for ((name, values) in enums) {
                append("<p><code>${name.escapeHtml()}</code>: ${values.joinToString(", ") { it.escapeHtml() }}</p>")
            }
        }

        if (encryptedAttributes.isNotEmpty()) {
            append("<h3>Encrypted Attributes</h3>")
            append("<p>${encryptedAttributes.joinToString(", ") { "<code>${it.escapeHtml()}</code>" }}</p>")
        }

        append("</div>")
    }

    fun toPlainText(): String = buildString {
        appendLine("Table: $tableName")
        appendLine("Primary Key: $primaryKey")
        appendLine()
        appendLine("── Columns ${"─".repeat(40)}")
        for (col in columns) {
            val nullable = if (col.nullable) "null" else "not null"
            val default = if (col.default != null) ", default: ${col.default}" else ""
            val limit = col.limitDescription().let { if (it.isNotEmpty()) ", $it" else "" }
            appendLine("  ${col.name.padEnd(24)} ${col.type.padEnd(12)} $nullable$default$limit")
        }
        if (indexes.isNotEmpty()) {
            appendLine()
            appendLine("── Indexes ${"─".repeat(40)}")
            for (idx in indexes) {
                val unique = if (idx.unique) " (unique)" else ""
                val using = if (idx.using != null) " using ${idx.using}" else ""
                appendLine("  ${idx.name}")
                appendLine("    columns: ${idx.columns.joinToString(", ")}$unique$using")
                if (idx.where != null) appendLine("    where: ${idx.where}")
            }
        }
        if (associations.isNotEmpty()) {
            appendLine()
            appendLine("── Associations ${"─".repeat(35)}")
            for (assoc in associations) {
                val extras = buildList {
                    if (assoc.through != null) add("through: ${assoc.through}")
                    if (assoc.polymorphic) add("polymorphic")
                    if (assoc.dependent != null) add("dependent: ${assoc.dependent}")
                }.let { if (it.isNotEmpty()) " [${it.joinToString(", ")}]" else "" }
                appendLine("  ${assoc.type.padEnd(16)} :${assoc.name} → ${assoc.className} (${assoc.foreignKey})$extras")
            }
        }
        if (enums.isNotEmpty()) {
            appendLine()
            appendLine("── Enums ${"─".repeat(42)}")
            for ((name, values) in enums) {
                appendLine("  $name: ${values.joinToString(", ")}")
            }
        }
        if (encryptedAttributes.isNotEmpty()) {
            appendLine()
            appendLine("── Encrypted ${"─".repeat(38)}")
            appendLine("  ${encryptedAttributes.joinToString(", ")}")
        }
    }

    fun findColumn(name: String): ColumnInfo? =
        columns.find { it.name.equals(name, ignoreCase = true) }
}

data class ColumnInfo(
    val name: String,
    val type: String,
    val nullable: Boolean,
    val default: String? = null,
    val limit: Int? = null,
    val precision: Int? = null,
    val scale: Int? = null,
    val comment: String? = null
) {
    fun toInlayText(): String {
        val parts = mutableListOf(type)
        if (!nullable) parts.add("not null")
        if (default != null) parts.add("default: $default")
        return parts.joinToString(", ")
    }

    fun limitDescription(): String = buildList {
        if (limit != null) add("limit: $limit")
        if (precision != null) add("precision: $precision")
        if (scale != null) add("scale: $scale")
    }.joinToString(", ")
}

data class IndexInfo(
    val name: String,
    val columns: List<String>,
    val unique: Boolean,
    val using: String? = null,
    val where: String? = null,
    val comment: String? = null
)

data class AssociationInfo(
    val type: String,
    val name: String,
    val className: String,
    val foreignKey: String,
    val through: String? = null,
    val polymorphic: Boolean = false,
    val dependent: String? = null
)
