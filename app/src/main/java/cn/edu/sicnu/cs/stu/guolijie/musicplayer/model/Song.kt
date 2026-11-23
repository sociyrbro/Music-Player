package cn.edu.sicnu.cs.stu.guolijie.musicplayer.model

import android.net.Uri

/**
 * 一首歌曲。数据来自 ContentResolver 查询 MediaStore 的结果。
 *
 * @param id       MediaStore 中的 _ID
 * @param title    歌曲名
 * @param artist   艺术家
 * @param duration 时长（毫秒）
 * @param uri      播放时交给 MediaPlayer 的 content:// 地址
 */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val duration: Long,
    val uri: Uri
)
