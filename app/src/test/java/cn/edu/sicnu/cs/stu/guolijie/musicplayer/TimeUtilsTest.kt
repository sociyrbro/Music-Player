package cn.edu.sicnu.cs.stu.guolijie.musicplayer

import cn.edu.sicnu.cs.stu.guolijie.musicplayer.util.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * TimeUtils 的单元测试：把毫秒格式化成 mm:ss。
 */
class TimeUtilsTest {

    @Test
    fun format_zeroMillis_returnsZeroTime() {
        assertEquals("00:00", TimeUtils.format(0))
    }

    @Test
    fun format_negativeMillis_returnsZeroTime() {
        assertEquals("00:00", TimeUtils.format(-100))
    }

    @Test
    fun format_lessThanOneMinute_keepsTwoDigits() {
        assertEquals("00:09", TimeUtils.format(9_000))
    }

    @Test
    fun format_oneMinuteTwentyThreeSeconds() {
        assertEquals("01:23", TimeUtils.format(83_000))
    }

    @Test
    fun format_longSong_overTenMinutes() {
        assertEquals("12:34", TimeUtils.format(754_000))
    }
}
