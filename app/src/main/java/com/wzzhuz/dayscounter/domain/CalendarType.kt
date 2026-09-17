package com.wzzhuz.dayscounter.domain

/** 日期所使用的历法 */
enum class CalendarType {
    GREGORIAN,

    /** 农历。需配合 lunarMonth / lunarDay 使用 */
    LUNAR;
}
