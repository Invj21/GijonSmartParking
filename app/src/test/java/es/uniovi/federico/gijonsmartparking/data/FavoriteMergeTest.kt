package es.uniovi.federico.gijonsmartparking.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteMergeTest {

    @Test
    fun server_list_without_pending_changes_is_used_as_is() {
        val merged = FavoriteMerge.merge(listOf(1L, 2L, 3L), emptySet(), emptySet())
        assertEquals(setOf(1L, 2L, 3L), merged.toSet())
    }

    @Test
    fun favorite_added_offline_is_kept_even_if_the_server_does_not_know_it() {
        val merged = FavoriteMerge.merge(listOf(1L), setOf(9L), emptySet())
        assertEquals(setOf(1L, 9L), merged.toSet())
    }

    @Test
    fun favorite_removed_offline_does_not_come_back_from_the_server() {
        val merged = FavoriteMerge.merge(listOf(1L, 2L), emptySet(), setOf(2L))
        assertEquals(setOf(1L), merged.toSet())
    }

    @Test
    fun result_has_no_duplicates() {
        val merged = FavoriteMerge.merge(listOf(1L, 1L, 2L), setOf(2L), emptySet())
        assertEquals(2, merged.size)
    }

    @Test
    fun empty_everywhere_gives_empty_list() {
        assertTrue(FavoriteMerge.merge(emptyList(), emptySet(), emptySet()).isEmpty())
    }

    @Test
    fun large_openstreetmap_ids_are_preserved() {
        val id = 1_234_567_890_123L
        assertEquals(listOf(id), FavoriteMerge.merge(listOf(id), emptySet(), emptySet()))
    }
}
