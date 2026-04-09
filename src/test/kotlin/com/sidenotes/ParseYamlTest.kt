package com.sidenotes

import com.sidenotes.services.AnnotationService
import org.junit.Assert.*
import org.junit.Test

class ParseYamlTest {

    // We can't instantiate AnnotationService without a Project, so we test
    // the YAML parsing logic and SafeConstructor behavior directly.

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
            assertTrue(e.message?.contains("could not determine a constructor") == true ||
                       e.message?.contains("Global tag is not allowed") == true)
        }
    }

    @Test
    fun `parses sidenotes gem wrapped YAML format`() {
        val yaml = """
            Application:
              metadata:
                table_name: applications
                primary_key: id
                enums:
                  channel: [undetermined, other, direct]
                encrypted_attributes: [ssn, dob]
              columns:
                - name: id
                  type: integer
                  nullable: false
                  limit: 8
                - name: email
                  type: string
                  nullable: false
                - name: score
                  type: decimal
                  nullable: true
                  precision: 10
                  scale: 2
              indexes:
                - name: index_applications_on_uuid
                  columns: [uuid]
                  unique: true
                  using: btree
              associations:
                - type: has_one
                  name: invite
                  class_name: InterviewInvite
                  foreign_key: invitable_id
                  dependent: destroy
        """.trimIndent()

        val loaderOptions = org.yaml.snakeyaml.LoaderOptions()
        val safeYaml = org.yaml.snakeyaml.Yaml(
            org.yaml.snakeyaml.constructor.SafeConstructor(loaderOptions)
        )
        val parsed: Map<String, Any> = safeYaml.load(yaml)

        // Verify wrapped structure
        assertTrue(parsed.containsKey("Application"))
        @Suppress("UNCHECKED_CAST")
        val root = parsed["Application"] as Map<String, Any>
        assertTrue(root.containsKey("metadata"))
        assertTrue(root.containsKey("columns"))

        @Suppress("UNCHECKED_CAST")
        val metadata = root["metadata"] as Map<String, Any>
        assertEquals("applications", metadata["table_name"])
        assertEquals("id", metadata["primary_key"])

        @Suppress("UNCHECKED_CAST")
        val enums = metadata["enums"] as Map<String, List<String>>
        assertEquals(listOf("undetermined", "other", "direct"), enums["channel"])

        @Suppress("UNCHECKED_CAST")
        val columns = root["columns"] as List<Map<String, Any>>
        assertEquals(3, columns.size)
        assertEquals(8, columns[0]["limit"])
        assertEquals(10, columns[2]["precision"])
        assertEquals(2, columns[2]["scale"])

        @Suppress("UNCHECKED_CAST")
        val indexes = root["indexes"] as List<Map<String, Any>>
        assertEquals("btree", indexes[0]["using"])

        @Suppress("UNCHECKED_CAST")
        val associations = root["associations"] as List<Map<String, Any>>
        assertEquals("destroy", associations[0]["dependent"])
    }

    @Test
    fun `CamelCase to snake_case conversion handles acronyms`() {
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
        assertEquals("application", camelToSnake("Application"))
        assertEquals("interview_invite", camelToSnake("InterviewInvite"))
    }
}
