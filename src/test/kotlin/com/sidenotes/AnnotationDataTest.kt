package com.sidenotes

import com.sidenotes.models.*
import org.junit.Assert.*
import org.junit.Test

class AnnotationDataTest {

    private val sampleAnnotation = AnnotationData(
        tableName = "users",
        primaryKey = "id",
        columns = listOf(
            ColumnInfo("id", "integer", false),
            ColumnInfo("email", "string", false),
            ColumnInfo("name", "string", true, "''"),
            ColumnInfo("age", "integer", true)
        ),
        indexes = listOf(
            IndexInfo("index_users_on_email", listOf("email"), true)
        ),
        associations = listOf(
            AssociationInfo("has_many", "posts", "Post", "user_id")
        )
    )

    @Test
    fun `toSummaryText includes counts`() {
        val summary = sampleAnnotation.toSummaryText()
        assertEquals("Table: users | 4 columns, 1 indexes, 1 associations", summary)
    }

    @Test
    fun `findColumn is case insensitive`() {
        assertNotNull(sampleAnnotation.findColumn("Email"))
        assertNotNull(sampleAnnotation.findColumn("EMAIL"))
        assertNotNull(sampleAnnotation.findColumn("email"))
        assertNull(sampleAnnotation.findColumn("nonexistent"))
    }

    @Test
    fun `toPlainText contains table name and columns`() {
        val text = sampleAnnotation.toPlainText()
        assertTrue(text.contains("Table: users"))
        assertTrue(text.contains("email"))
        assertTrue(text.contains("string"))
        assertTrue(text.contains("── Indexes"))
        assertTrue(text.contains("── Associations"))
    }

    @Test
    fun `toHtmlDocumentation escapes HTML in values`() {
        val malicious = AnnotationData(
            tableName = "<script>alert('xss')</script>",
            primaryKey = "id",
            columns = listOf(
                ColumnInfo("<b>evil</b>", "string", true)
            ),
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
    fun `toHtmlDocumentation has no hardcoded theme colors`() {
        val html = sampleAnnotation.toHtmlDocumentation()
        // Should not contain Darcula-specific hex colors
        assertFalse(html.contains("#2B2B2B"))
        assertFalse(html.contains("#A9B7C6"))
        assertFalse(html.contains("#CC7832"))
        assertFalse(html.contains("#FFC66D"))
    }

    @Test
    fun `ColumnInfo toInlayText formats correctly`() {
        assertEquals("string, not null", ColumnInfo("email", "string", false).toInlayText())
        assertEquals("integer", ColumnInfo("age", "integer", true).toInlayText())
        assertEquals("string, not null, default: ''", ColumnInfo("name", "string", false, "''").toInlayText())
    }
}
