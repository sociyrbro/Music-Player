package cn.edu.sicnu.cs.stu.guolijie.musicplayer.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import cn.edu.sicnu.cs.stu.guolijie.musicplayer.model.Song

/**
 * 用 ContentResolver 查询 MediaStore，得到手机里的音乐文件列表。
 */
object SongLoader {

    fun load(context: Context): List<Song> {
        val songs = mutableListOf<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION
        )
        // IS_MUSIC != 0 过滤掉铃声、录音等非音乐文件；
        // DURATION > 0 跳过还没有被媒体库扫描完成（或加密的 kgg/kgm）条目
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
            "${MediaStore.Audio.Media.DURATION} > 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "未知歌曲"
                val rawArtist = cursor.getString(artistColumn)
                // MediaStore 查不到艺术家时会给字符串 "<unknown>"
                val artist = if (rawArtist.isNullOrBlank() || rawArtist == "<unknown>") {
                    context.getString(cn.edu.sicnu.cs.stu.guolijie.musicplayer.R.string.unknown_artist)
                } else {
                    rawArtist
                }
                val duration = cursor.getLong(durationColumn)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                songs.add(Song(id, title, artist, duration, uri))
            }
        }
        return songs
    }
}
