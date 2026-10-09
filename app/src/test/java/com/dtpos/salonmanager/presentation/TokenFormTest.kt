package com.dtpos.salonmanager.presentation

import com.dtpos.salonmanager.data.database.entities.ServiceEntity
import com.dtpos.salonmanager.presentation.tokens.TokenDraft
import com.dtpos.salonmanager.presentation.tokens.isSuggestable
import com.dtpos.salonmanager.presentation.tokens.serviceSuggestions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The token / booking form: when customers are suggested, which services are offered, how several are kept. */
class TokenFormTest {

    private fun service(name: String, price: Long = 50_000) =
        ServiceEntity(businessId = 1, name = name, category = "Hair", priceMinor = price, createdAt = 0, updatedAt = 0)

    private val menu = listOf(service("Hair cut"), service("Beard trim"), service("Kids hair cut"), service("Facial"), service("Hair color"))

    @Test
    fun `customers are suggested from two letters of a name or three digits of a phone`() {
        assertFalse("A".isSuggestable())
        assertTrue("Al".isSuggestable())
        assertFalse("03".isSuggestable())
        assertTrue("030".isSuggestable())
        assertTrue("0300-12".isSuggestable())
        assertFalse("  ".isSuggestable())
    }

    @Test
    fun `typed text finds menu services, best match first, without the chosen ones`() {
        assertEquals(listOf("Hair cut", "Hair color", "Kids hair cut"), serviceSuggestions(menu, "hair", emptyList()).map { it.name })
        assertEquals(listOf("Hair color", "Kids hair cut"), serviceSuggestions(menu, "hair", listOf("hair CUT")).map { it.name })
        assertEquals(listOf("Facial"), serviceSuggestions(menu, " fac ", emptyList()).map { it.name })
        assertTrue(serviceSuggestions(menu, "massage", emptyList()).isEmpty())
        // Nothing typed: the menu's first services as quick picks.
        assertEquals(3, serviceSuggestions(menu, "", emptyList(), limit = 3).size)
    }

    @Test
    fun `several services are kept as one line without repeats`() {
        assertEquals("Hair cut, Beard trim, Head massage", TokenDraft.joinServices(listOf("Hair cut", " Beard trim ", "HAIR CUT", "", "Head massage")))
        assertEquals("", TokenDraft.joinServices(emptyList()))
    }
}
