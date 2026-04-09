package com.sidenotes

import com.sidenotes.models.*
import org.junit.Assert.*
import org.junit.Test

class AnnotationDataTest {

    private val sampleAnnotation = AnnotationData(
        tableName = "users",
        primaryKey = "id",
        columns = listOf(
            ColumnInfo("id", "integer", false, limit = 8),
            ColumnInfo("email", "string", false),
            ColumnInfo("name", "string", true, "''"),
            ColumnInfo("score", "decimal", true, precision = 10, scale = 2),
            ColumnInfo("age", "integer", true)
        ),
        indexes = listOf(
            IndexInfo("index_users_on_email", listOf("email"), true, using = "btree")
        ),
        associations = listOf(
            AssociationInfo("has_many", "posts", "Post", "user_id", dependent = "destroy"),
            AssociationInfo("has_one", "profile", "Profile", "user_id", through = "account")
        ),
        enums = mapOf("role" to listOf("admin", "user", "guest")),
        encryptedAttributes = listOf("ssn")
    )

    @Test
    fun `toSummaryText includes counts`() {
        val summary = sampleAnnotation.toSummaryText()
        assertEquals("Table: users | 5 columns, 1 indexes, 2 associations", summary)
    }

    @Test
    fun `findColumn is case insensitive`() {
        assertNotNull(sampleAnnotation.findColumn("Email"))
        assertNotNull(sampleAnnotation.findColumn("EMAIL"))
        assertNotNull(sampleAnnotation.findColumn("email"))
        assertNull(sampleAnnotation.findColumn("nonexistent"))
    }

    @Test
    fun `toPlainText contains all sections`() {
        val text = sampleAnnotation.toPlainText()
        assertTrue(text.contains("Table: users"))
        assertTrue(text.contains("email"))
        assertTrue(text.contains("── Indexes"))
        assertTrue(text.contains("btree"))
        assertTrue(text.contains("── Associations"))
        assertTrue(text.contains("dependent: destroy"))
        assertTrue(text.contains("through: account"))
        assertTrue(text.contains("── Enums"))
        assertTrue(text.contains("role: admin, user, guest"))
        assertTrue(text.contains("── Encrypted"))
        assertTrue(text.contains("ssn"))
    }

    @Test
    fun `toPlainText shows limit precision scale`() {
        val text = sampleAnnotation.toPlainText()
        assertTrue(text.contains("limit: 8"))
        assertTrue(text.contains("precision: 10"))
        assertTrue(text.contains("scale: 2"))
    }

    @Test
    fun `toHtmlDocumentation escapes HTML in values`() {
        val malicious = AnnotationData(
            tableName = "<script>alert('xss')</script>",
            primaryKey = "id",
            columns = listOf(ColumnInfo("<b>evil</b>", "string", true)),
            indexes = emptyList(),
            associations = emptyList()
        )

        val html = malicious.toHtmlDocumentation()
        assertFalse(html.contains("<script>"))
        assertFalse(html.contains("<b>evil</b>"))
        assertTrue(html.contains("&lt;script&gt;"))
        assertTrue(html.contains("&lt;b&gt;evil&lt;/b&gt;"))
    }

    @Test
    fun `toHtmlDocumentation includes enums and associations options`() {
        val html = sampleAnnotation.toHtmlDocumentation()
        assertTrue(html.contains("Enums"))
        assertTrue(html.contains("role"))
        assertTrue(html.contains("admin, user, guest"))
        assertTrue(html.contains("Encrypted"))
        assertTrue(html.contains("ssn"))
        assertTrue(html.contains("dependent: destroy"))
        assertTrue(html.contains("through: account"))
    }

    @Test
    fun `ColumnInfo toInlayText formats correctly`() {
        assertEquals("string, not null", ColumnInfo("email", "string", false).toInlayText())
        assertEquals("integer", ColumnInfo("age", "integer", true).toInlayText())
        assertEquals("string, not null, default: ''", ColumnInfo("name", "string", false, "''").toInlayText())
    }

    @Test
    fun `ColumnInfo limitDescription formats correctly`() {
        assertEquals("limit: 8", ColumnInfo("id", "integer", false, limit = 8).limitDescription())
        assertEquals("precision: 10, scale: 2", ColumnInfo("x", "decimal", true, precision = 10, scale = 2).limitDescription())
        assertEquals("", ColumnInfo("name", "string", true).limitDescription())
    }
}
