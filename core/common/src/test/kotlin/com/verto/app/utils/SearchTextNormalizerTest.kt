package com.verto.app.utils

import org.junit.Assert.*
import org.junit.Test

class SearchTextNormalizerTest {
    @Test fun `arabic variants and diacritics are folded`() { assertEquals("احمد علي", SearchTextNormalizer.text("أَحْمَد عَلى")) }
    @Test fun `arabic and persian digits become ascii`() { assertEquals("0123456789", SearchTextNormalizer.phone("٠١٢٣٤۵۶۷۸۹")) }
    @Test fun `identifier removes punctuation and spaces`() { assertEquals("ab123", SearchTextNormalizer.identifier("AB- 123")) }
    @Test fun `one character query is delayed`() { assertNull(SearchTextNormalizer.query("أ")); assertNotNull(SearchTextNormalizer.query("أب")) }
    @Test fun `phone query can activate search`() { val q=SearchTextNormalizer.query("+٢٤٩"); assertEquals("249", q?.phone) }
}
