package cn.edu.sicnu.cs.stu.guolijie.musicplayer.util

import java.util.Locale

/**
 * 把毫秒时长格式化成 mm:ss，例如 83000 -> 01:23。
 */
object TimeUtils {

    fun format(millis: Long): String {
        if (millis <= 0) return "00:00"
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
