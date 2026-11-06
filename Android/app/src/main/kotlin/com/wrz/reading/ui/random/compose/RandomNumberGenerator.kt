package com.wrz.reading.ui.random.compose

import java.util.Random

/**
 * 随机数生成逻辑：从原 RandomFragment 原样抽取，便于 JVM 单元测试覆盖。
 * 保持原有语义：范围取 start/end 的最值，不允许重复时耗尽范围返回 [EXHAUSTED]。
 */
internal object RandomNumberGenerator {

    /** 范围内已无可用数字（与原先 -1 哨兵值一致） */
    const val EXHAUSTED = -1

    fun generate(
        random: Random,
        start: Int,
        end: Int,
        existing: List<String>,
        allowDuplicates: Boolean
    ): Int {
        val max = maxOf(start, end)
        val min = minOf(start, end)
        val range = max - min + 1

        var num = random.nextInt(range) + min
        if (!allowDuplicates) {
            if (existing.size >= range) {
                return EXHAUSTED
            }
            // 迭代重试，避免递归栈溢出
            var attempts = 0
            while (existing.contains(num.toString())) {
                num = random.nextInt(range) + min
                if (++attempts > range * 4) break
            }
        }
        return num
    }
}
