package cn.edu.sicnu.cs.stu.guolijie.musicplayer.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cn.edu.sicnu.cs.stu.guolijie.musicplayer.MainActivity
import cn.edu.sicnu.cs.stu.guolijie.musicplayer.R
import cn.edu.sicnu.cs.stu.guolijie.musicplayer.data.SongLoader
import cn.edu.sicnu.cs.stu.guolijie.musicplayer.model.Song

/**
 * 音乐播放服务：播放、暂停、停止、上一首、下一首、拖动进度全部在这里完成，
 * Activity 通过 Binder 拿到 Service 实例后调用这些方法，并读取歌曲名、播放进度。
 */
class MusicService : Service() {

    /** 暴露给 Activity 的 Binder，Activity 用它拿到 Service 对象 */
    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    private val binder = MusicBinder()

    /** 歌曲列表，由 ContentResolver 查询得到 */
    val songs = mutableListOf<Song>()

    private var player: MediaPlayer? = null
    private var currentIndex = -1

    /** 歌曲切换时的回调，Activity 里赋值，用来刷新界面 */
    var onSongChanged: ((Song?) -> Unit)? = null

    /** 播放/暂停状态变化的回调 */
    var onPlayStateChanged: ((Boolean) -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    /** 用 ContentResolver 读取音乐列表，返回歌曲数量 */
    fun loadSongs(): Int {
        songs.clear()
        songs.addAll(SongLoader.load(this))
        return songs.size
    }

    /** 播放第 index 首 */
    fun play(index: Int) {
        if (index < 0 || index >= songs.size) return
        currentIndex = index
        releasePlayer()
        val song = songs[index]
        player = MediaPlayer().apply {
            setDataSource(this@MusicService, song.uri)
            setOnPreparedListener { mp ->
                mp.start()
                onPlayStateChanged?.invoke(true)
            }
            setOnCompletionListener {
                // 一首放完自动播下一首
                next()
            }
            setOnErrorListener { _, _, _ ->
                // 文件损坏或格式不支持时给出提示，并把播放器释放掉，避免停在出错状态
                Toast.makeText(
                    this@MusicService,
                    getString(R.string.play_error, song.title),
                    Toast.LENGTH_SHORT
                ).show()
                onPlayStateChanged?.invoke(false)
                releasePlayer()
                true
            }
            prepareAsync()
        }
        showNotification(song)
        onSongChanged?.invoke(song)
    }

    /** 播放/暂停切换 */
    fun togglePlay() {
        if (isPlaying()) pause() else resume()
    }

    fun pause() {
        player?.let {
            if (it.isPlaying) {
                it.pause()
                onPlayStateChanged?.invoke(false)
            }
        }
    }

    fun resume() {
        val current = player
        if (current == null) {
            if (songs.isNotEmpty()) play(if (currentIndex >= 0) currentIndex else 0)
        } else {
            try {
                if (!current.isPlaying) {
                    current.start()
                    onPlayStateChanged?.invoke(true)
                }
            } catch (e: IllegalStateException) {
                e.printStackTrace()
            }
        }
    }

    /** 停止播放，清空当前歌曲并结束服务 */
    fun stop() {
        releasePlayer()
        currentIndex = -1
        cancelNotification()
        onPlayStateChanged?.invoke(false)
        onSongChanged?.invoke(null)
        stopSelf()
    }

    fun next() {
        if (songs.isEmpty()) return
        play((currentIndex + 1) % songs.size)
    }

    fun previous() {
        if (songs.isEmpty()) return
        play(if (currentIndex <= 0) songs.size - 1 else currentIndex - 1)
    }

    /** 拖动进度条时调用 */
    fun seekTo(position: Int) {
        try {
            player?.seekTo(position)
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }
    }

    /** 当前播放位置（毫秒） */
    fun position(): Int = try {
        player?.currentPosition ?: 0
    } catch (e: IllegalStateException) {
        0
    }

    /** 当前歌曲总时长（毫秒） */
    fun duration(): Int = try {
        player?.duration ?: 0
    } catch (e: IllegalStateException) {
        0
    }

    fun isPlaying(): Boolean = try {
        player?.isPlaying == true
    } catch (e: IllegalStateException) {
        false
    }

    fun currentSong(): Song? = songs.getOrNull(currentIndex)

    fun currentIndex(): Int = currentIndex

    private fun releasePlayer() {
        player?.let {
            try {
                if (it.isPlaying) it.stop()
            } catch (e: IllegalStateException) {
                e.printStackTrace()
            }
            it.release()
        }
        player = null
    }

    override fun onDestroy() {
        releasePlayer()
        cancelNotification()
        super.onDestroy()
    }

    // ------------------------------------------------------------ 通知栏

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
            }
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    /** 每播放一首新歌，就把歌名显示到通知栏 */
    @SuppressLint("MissingPermission")
    private fun showNotification(song: Song) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(getString(R.string.notification_title, song.title))
            .setContentText(song.artist)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }

    private fun cancelNotification() {
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
    }

    companion object {
        private const val CHANNEL_ID = "music_playback"
        private const val NOTIFICATION_ID = 1001
    }
}
