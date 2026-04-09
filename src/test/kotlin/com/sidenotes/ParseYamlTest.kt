package com.sidenotes

import com.sidenotes.services.AnnotationService
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests parseYaml independently — it's an internal method, so we access it
 * via a test-scoped subclass or direct instantiation isn't possible without
 * the IntelliJ Project. Instead we test the YAML parsing logic inline.
 *
 * These tests use SnakeYAML directly with SafeConstructor to verify the
 * same parsing logic used by AnnotationService.
 */
class ParseYamlTest {

    @Test
    fun `parses valid YAML with all fields`() {
        val yaml = """
            table_name: users
            primary_key: id
            columns:
              - name: id
                type: integer
                nullable: false
              - name: email
                type: string
                nullable: false
              - name: bio
                type: text
                nullable: true
                default: ""
            indexes:
              - name: index_users_on_email
                columns:
                  - email
                unique: true
            associations:
              - type: has_many
                name: posts
                class_name: Post
                foreign_key: user_id
        """.trimIndent()

        val loaderOptions = org.yaml.snakeyaml.LoaderOptions()
        val safeYaml = org.yaml.snakeyaml.Yaml(
            org.yaml.snakeyaml.constructor.SafeConstructor(loaderOptions)
        )
        val data: Map<String, Any> = safeYaml.load(yaml)

        assertEquals("users", data["table_name"])
        assertEquals("id", data["primary_key"])

        @Suppress("UNCHECKED_CAST")
        val columns = data["columns"] as List<Map<String, Any>>
        assertEquals(3, columns.size)
        assertEquals("id", columns[0]["name"])
        assertEquals("integer", columns[0]["type"])
        assertEquals(false, columns[0]["nullable"])

        @Suppress("UNCHECKED_CAST")
        val indexes = data["indexes"] as List<Map<String, Any>>
        assertEquals(1, indexes.size)
        assertEquals(true, indexes[0]["unique"])

        @Suppress("UNCHECKED_CAST")
        val associations = data["associations"] as List<Map<String, Any>>
        assertEquals("has_many", associations[0]["type"])
    }

    @Test
    fun `SafeConstructor rejects arbitrary object deserialization`() {
        val maliciousYaml = "exploit: !!javax.script.ScriptEngineManager []\n"

        val loaderOptions = org.yaml.snakeyaml.LoaderOptions()
        val safeYaml = org.yaml.snakeyaml.Yaml(
            org.yaml.snakeyaml.constructor.SafeConstructor(loaderOptions)
        )

        try {
            safeYaml.load<Any>(maliciousYaml)
            fail("Should have thrown an exception for unsafe YAML tag")
        } catch (e: Exception) {
            // SafeConstructor correctly rejects the !! tag
            assertTrue(e.message?.contains("could not determine a constructor") == true ||
                       e.message?.contains("Global tag is not allowed") == true)
        }
    }

    @Test
    fun `parses YAML with missing optional fields`() {
        val yaml = """
            table_name: widgets
        """.trimIndent()

        val loaderOptions = org.yaml.snakeyaml.LoaderOptions()
        val safeYaml = org.yaml.snakeyaml.Yaml(
            org.yaml.snakeyaml.constructor.SafeConstructor(loaderOptions)
        )
        val data: Map<String, Any> = safeYaml.load(yaml)

        assertEquals("widgets", data["table_name"])
        assertNull(data["primary_key"])
        assertNull(data["columns"])
    }

    @Test
    fun `CamelCase to snake_case conversion handles acronyms`() {
        // Test the regex logic from getAnnotationForClassName
        fun camelToSnake(className: String): String =
            className
                .replace("::", "/")
                .replace(Regex("([A-Z]+)([A-Z][a-z])")) { "${it.groupValues[1]}_${it.groupValues[2]}" }
                .replace(Regex("([a-z\\d])([A-Z])")) { "${it.groupValues[1]}_${it.groupValues[2]}" }
                .lowercase()

        assertEquals("user", camelToSnake("User"))
        assertEquals("admin/user", camelToSnake("Admin::User"))
        assertEquals("api_key", camelToSnake("APIKey"))
        assertEquals("html_parser", camelToSnake("HTMLParser"))
        assertEquals("my_api_client", camelToSnake("MyAPIClient"))
        assertEquals("simple", camelToSnake("Simple"))
    }
}
