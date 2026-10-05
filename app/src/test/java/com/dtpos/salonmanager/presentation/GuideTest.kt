package com.dtpos.salonmanager.presentation

import com.dtpos.salonmanager.presentation.settings.Guide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The in-app A-Z guide: every language has the same topics, each with steps. */
class GuideTest {
    private fun topics(tag: String) = Guide.parse(File("src/main/assets/guide/$tag.md").readText())

    @Test
    fun `every language has the same topics with steps`() {
        val en = topics("en")
        assertTrue(en.size >= 20)
        listOf("ur", "ur-Latn").forEach { tag ->
            val other = topics(tag)
            assertEquals("topics in $tag", en.size, other.size)
            other.forEachIndexed { i, t -> assertEquals("steps of '${t.title}' in $tag", en[i].steps.size, t.steps.size) }
        }
        en.forEach { assertTrue(it.title, it.steps.isNotEmpty()) }
    }

    @Test
    fun `parser reads titles and steps`() {
        val parsed = Guide.parse("## One\n- a\n- b\n\n## Two\n- c")
        assertEquals(listOf("One", "Two"), parsed.map { it.title })
        assertEquals(listOf("a", "b"), parsed[0].steps)
    }
}
