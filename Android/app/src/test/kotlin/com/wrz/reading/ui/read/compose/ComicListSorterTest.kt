package com.wrz.reading.ui.read.compose

import com.wrz.reading.ui.read.model.Collection
import com.wrz.reading.ui.read.model.Comic
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 集合详情搜索/排序逻辑回归测试：对齐原 rebuildDisplayList() 的分支。
 */
class ComicListSorterTest {

    private fun comic(
        title: String,
        size: Long,
        progress: Int,
        lastRead: Long,
        directory: Boolean = false
    ): Comic = Comic().apply {
        this.title = title
        this.fileSize = size
        this.readProgress = progress
        this.lastRead = lastRead
        this.isDirectory = directory
    }

    private fun titles(list: List<Comic>): List<String> = list.map { it.title }

    @Test
    fun searchFiltersByTitleIgnoringCase() {
        val list = listOf(
            comic("One Piece", 1, 1, 1),
            comic("Naruto", 2, 2, 2),
            comic("Bleach", 3, 3, 3)
        )
        val result = ComicListSorter.sortAndFilter(list, "pie", Collection.SORT_BY_NAME)
        assertEquals(listOf("One Piece"), titles(result))
    }

    @Test
    fun naturalNameSortKeepsNumbersInOrder() {
        val list = listOf(
            comic("Chapter10", 1, 0, 0),
            comic("Chapter2", 1, 0, 0),
            comic("Chapter1", 1, 0, 0)
        )
        val result = ComicListSorter.sortAndFilter(list, "", Collection.SORT_BY_NAME)
        assertEquals(listOf("Chapter1", "Chapter2", "Chapter10"), titles(result))
    }

    @Test
    fun reversedNameSort() {
        val list = listOf(
            comic("A", 1, 0, 0),
            comic("B", 1, 0, 0)
        )
        val result = ComicListSorter.sortAndFilter(list, "", Collection.SORT_BY_NAME_REVERSE)
        assertEquals(listOf("B", "A"), titles(result))
    }

    @Test
    fun sizeSortIsDescending() {
        val list = listOf(
            comic("small", 10, 0, 0),
            comic("large", 100, 0, 0)
        )
        val result = ComicListSorter.sortAndFilter(list, "", Collection.SORT_BY_SIZE)
        assertEquals(listOf("large", "small"), titles(result))
    }

    @Test
    fun progressSortIsDescending() {
        val list = listOf(
            comic("low", 1, 1, 0),
            comic("high", 1, 9, 0)
        )
        val result = ComicListSorter.sortAndFilter(list, "", Collection.SORT_BY_PROGRESS)
        assertEquals(listOf("high", "low"), titles(result))
    }

    @Test
    fun lastReadSortIsDescending() {
        val list = listOf(
            comic("old", 1, 0, 100),
            comic("new", 1, 0, 200)
        )
        val result = ComicListSorter.sortAndFilter(
            list,
            "",
            Collection.SORT_BY_LAST_READ_TIME
        )
        assertEquals(listOf("new", "old"), titles(result))
    }

    @Test
    fun directoriesAlwaysComeFirst() {
        val list = listOf(
            comic("a-file", 1, 0, 0),
            comic("z-directory", 1, 0, 0, directory = true)
        )
        val result = ComicListSorter.sortAndFilter(list, "", Collection.SORT_BY_NAME)
        assertEquals(listOf("z-directory", "a-file"), titles(result))
    }
}
