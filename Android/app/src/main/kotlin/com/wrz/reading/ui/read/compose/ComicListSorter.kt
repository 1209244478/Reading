package com.wrz.reading.ui.read.compose

import com.wrz.reading.ui.read.model.Collection
import com.wrz.reading.ui.read.model.Comic
import com.wrz.reading.ui.read.model.FileType
import com.wrz.reading.ui.read.utils.FileSorter
import com.wrz.reading.ui.read.utils.VideoUtils
import java.util.Locale

/**
 * 集合详情列表的搜索 + 排序逻辑，从原 rebuildDisplayList() 原样抽取。
 * 排序/过滤为纯内存操作，可在主线程执行。
 */
internal object ComicListSorter {

    fun sortAndFilter(all: List<Comic>, keyword: String, sort: Int): List<Comic> {
        val filtered = ArrayList<Comic>(all.size)
        for (comic in all) {
            if (keyword.isEmpty() ||
                comic.title.lowercase(Locale.getDefault()).contains(keyword)
            ) {
                filtered.add(comic)
            }
        }

        // 目录始终排在最前
        val dirFirst = java.util.Comparator<Comic> { a, b ->
            java.lang.Boolean.compare(!a.isDirectory, !b.isDirectory)
        }

        when (sort) {
            Collection.SORT_BY_NAME -> FileSorter.sortComics(dirFirst, filtered, false)
            Collection.SORT_BY_NAME_REVERSE -> FileSorter.sortComics(dirFirst, filtered, true)
            Collection.SORT_BY_SIZE -> filtered.sortWith(
                dirFirst.thenComparing(
                    java.util.Comparator<Comic> { a, b ->
                        java.lang.Long.compare(b.fileSize, a.fileSize)
                    }
                )
            )

            Collection.SORT_BY_PROGRESS -> filtered.sortWith(
                dirFirst.thenComparing(
                    java.util.Comparator<Comic> { a, b ->
                        java.lang.Long.compare(b.readProgress.toLong(), a.readProgress.toLong())
                    }
                )
            )

            Collection.SORT_BY_LAST_READ_TIME -> filtered.sortWith(
                dirFirst.thenComparing(
                    java.util.Comparator<Comic> { a, b ->
                        java.lang.Long.compare(b.lastRead, a.lastRead)
                    }
                )
            )
        }
        return filtered
    }
}

/** 漫画项进度文案与进度条比例，对应 ComicAdapter.convert() 中的分支。 */
internal data class ComicProgressInfo(
    val text: String,
    val fraction: Float
)

internal fun comicProgressInfo(comic: Comic): ComicProgressInfo {
    val fileType = comic.fileTypeEnum
    val max = comic.total.toInt()

    val (text, current) = if (fileType == FileType.VIDEO || fileType == FileType.MUSIC) {
        Pair(
            VideoUtils.formatTime(comic.videoPosition) + "-" + VideoUtils.formatTime(comic.total),
            comic.videoPosition.toInt()
        )
    } else if (comic.subProgress != null &&
        comic.subProgress.isNotEmpty() &&
        fileType == FileType.ZIP
    ) {
        Pair(
            (comic.addUpSubProgress + 1).toString() + "/" + comic.total,
            comic.addUpSubProgress.toInt()
        )
    } else {
        Pair(
            (comic.readProgress + 1).toString() + "/" + comic.total,
            comic.readProgress
        )
    }

    val fraction = if (max > 0) {
        (current.toFloat() / max.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    return ComicProgressInfo(text, fraction)
}

/** 漫画体积文案，对应 ComicAdapter.convert() 中按文件类型分支的拼接方式。 */
internal fun comicCacheText(comic: Comic): String {
    val fileType = comic.fileTypeEnum
    return when {
        fileType == FileType.ZIP ->
            "大小:" + com.wrz.reading.ui.read.utils.FileUtils.formatFileSizeToString(comic.fileSize) +
                "\n缓存大小:" +
                com.wrz.reading.ui.read.utils.FileUtils.formatFileSizeToString(comic.cacheSize)

        fileType != FileType.IMAGE ->
            "大小:" + com.wrz.reading.ui.read.utils.FileUtils.formatFileSizeToString(comic.fileSize)

        else ->
            "缓存大小:" +
                com.wrz.reading.ui.read.utils.FileUtils.formatFileSizeToString(comic.cacheSize)
    }
}
