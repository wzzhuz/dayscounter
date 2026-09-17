package com.wzzhuz.dayscounter.domain

/**
 * 事件的计数模式。
 *
 * 两种模式**有意不对称**，这不是 bug：
 * - COUNTDOWN（倒数）用「间隔」语义：明天到今天相差 1 天
 * - COUNTUP（正数）用「序数」语义：出生当天就是人生第 1 天
 *
 * 不能靠「目标日在过去还是未来」自动推断，
 * 否则过期的春节倒数会显示成「春节第 3 天」。
 */
enum class CountMode {
    /** 倒数：不含起始日。今天=0，明天=还剩 1 天 */
    COUNTDOWN,

    /** 正数：包含起始日。今天=第 1 天，昨天=第 2 天 */
    COUNTUP;

    companion object {
        /** 新建事件时的默认值：未来倒数，过去正数 */
        fun defaultFor(daysUntil: Int): CountMode =
            if (daysUntil >= 0) COUNTDOWN else COUNTUP
    }
}
