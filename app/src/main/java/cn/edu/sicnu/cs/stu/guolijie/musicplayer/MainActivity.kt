package cn.edu.sicnu.cs.stu.guolijie.musicplayer

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.edu.sicnu.cs.stu.guolijie.musicplayer.adapter.SongAdapter
import cn.edu.sicnu.cs.stu.guolijie.musicplayer.model.Song
import cn.edu.sicnu.cs.stu.guolijie.musicplayer.service.MusicService
import cn.edu.sicnu.cs.stu.guolijie.musicplayer.util.TimeUtils
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

/**
 * 界面层：只负责显示和转发点击事件，
 * 真正的播放逻辑全部在 MusicService 中，通过 Binder 调用。
 */
class MainActivity : AppCompatActivity() {

    private var musicService: MusicService? = null
    private var bound = false

    private lateinit var rvSongs: RecyclerView
    private lateinit var sbProgress: SeekBar
    private lateinit var tvTitle: TextView
    private lateinit var tvArtist: TextView
    private lateinit var tvCurrent: TextView
    private lateinit var tvTotal: TextView
    private lateinit var tvTip: TextView
    private lateinit var btnPlay: MaterialButton

    private var adapter: SongAdapter? = null

    /** 用户正在拖动进度条时，后台线程不要覆盖进度 */
    private var userSeeking = false
    private var lastPlaying = false
    private var lastDuration = -1

    private val uiHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var progressRunning = false
    private var progressThread: Thread? = null

    /** 绑定服务：拿到 MusicService 后注册回调，并读取歌曲列表 */
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? MusicService.MusicBinder ?: return
            musicService = binder.getService()
            bound = true
            musicService?.onSongChanged = { song -> uiHandler.post { showSong(song) } }
            musicService?.onPlayStateChanged = { playing -> uiHandler.post { updatePlayButton(playing) } }
            if (hasAudioPermission()) {
                loadSongs()
            } else {
                tvTip.text = getString(R.string.permission_denied)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            bound = false
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (hasAudioPermission()) {
            loadSongs()
        } else {
            tvTip.text = getString(R.string.permission_denied)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applyWindowInsets(findViewById(R.id.root))

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = getString(R.string.app_name)
        supportActionBar?.subtitle = getString(R.string.toolbar_subtitle)

        rvSongs = findViewById(R.id.rvSongs)
        sbProgress = findViewById(R.id.sbProgress)
        tvTitle = findViewById(R.id.tvTitle)
        tvArtist = findViewById(R.id.tvArtist)
        tvCurrent = findViewById(R.id.tvCurrent)
        tvTotal = findViewById(R.id.tvTotal)
        tvTip = findViewById(R.id.tvTip)
        btnPlay = findViewById(R.id.btnPlay)

        rvSongs.layoutManager = LinearLayoutManager(this)

        btnPlay.setOnClickListener { musicService?.togglePlay() }
        findViewById<MaterialButton>(R.id.btnPrev).setOnClickListener { musicService?.previous() }
        findViewById<MaterialButton>(R.id.btnNext).setOnClickListener { musicService?.next() }
        findViewById<MaterialButton>(R.id.btnStop).setOnClickListener {
            musicService?.stop()
            resetUi()
        }

        // 拖动进度条 -> 调用服务的 seekTo
        sbProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) tvCurrent.text = TimeUtils.format(progress.toLong())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                userSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                musicService?.seekTo(seekBar?.progress ?: 0)
                userSeeking = false
            }
        })

        startAndBindService()
        if (!hasAudioPermission()) {
            permissionLauncher.launch(requiredPermissions())
        }
    }

    /** startService 保证解绑后还能继续播放，bindService 用来拿 Binder */
    private fun startAndBindService() {
        val intent = Intent(this, MusicService::class.java)
        startService(intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    /** 通过 ContentResolver 读取歌曲列表并显示 */
    private fun loadSongs() {
        val service = musicService ?: return
        val count = service.loadSongs()
        if (adapter == null) {
            adapter = SongAdapter(service.songs) { index ->
                service.play(index)
                showSong(service.currentSong())
            }
            rvSongs.adapter = adapter
        } else {
            adapter?.notifyDataSetChanged()
        }
        tvTip.text = if (count == 0) {
            getString(R.string.no_music)
        } else {
            getString(R.string.song_count, count)
        }
        showSong(service.currentSong())
        updatePlayButton(service.isPlaying())
        startProgressThread()
    }

    /** 显示当前歌曲信息 */
    private fun showSong(song: Song?) {
        if (song == null) {
            tvTitle.text = getString(R.string.not_playing)
            tvArtist.text = ""
            adapter?.playingIndex = -1
            sbProgress.progress = 0
            tvCurrent.text = getString(R.string.time_zero)
            tvTotal.text = getString(R.string.time_zero)
            return
        }
        tvTitle.text = song.title
        tvArtist.text = song.artist
        adapter?.playingIndex = musicService?.currentIndex() ?: -1
    }

    private fun updatePlayButton(playing: Boolean) {
        lastPlaying = playing
        btnPlay.text = getString(if (playing) R.string.btn_pause else R.string.btn_play)
    }

    private fun resetUi() {
        userSeeking = false
        showSong(null)
        updatePlayButton(false)
    }

    /** 后台线程每 300ms 读一次服务里的播放进度，再交给主线程刷新 SeekBar */
    private fun startProgressThread() {
        if (progressRunning) return
        progressRunning = true
        progressThread = Thread {
            while (progressRunning) {
                val service = musicService
                if (service != null) {
                    val position = service.position()
                    val duration = service.duration()
                    val playing = service.isPlaying()
                    uiHandler.post { updateProgress(position, duration, playing) }
                }
                try {
                    Thread.sleep(300)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun updateProgress(position: Int, duration: Int, playing: Boolean) {
        tvTotal.text = TimeUtils.format(duration.toLong())
        // 只在总时长变化时重设 max，避免拖动进度条时被反复重置
        if (duration != lastDuration) {
            lastDuration = duration
            sbProgress.max = if (duration > 0) duration else 1
        }
        if (!userSeeking) {
            sbProgress.progress = position
            tvCurrent.text = TimeUtils.format(position.toLong())
        }
        if (playing != lastPlaying) {
            updatePlayButton(playing)
        }
    }

    private fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun requiredPermissions(): Array<String> {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return permissions.toTypedArray()
    }

    /** Android 15 起强制沉浸式，需要自己给内容让出状态栏和导航栏的高度 */
    private fun applyWindowInsets(root: View) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = true
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
    }

    override fun onDestroy() {
        progressRunning = false
        progressThread?.interrupt()
        progressThread = null
        uiHandler.removeCallbacksAndMessages(null)
        musicService?.onSongChanged = null
        musicService?.onPlayStateChanged = null
        if (bound) {
            unbindService(connection)
            bound = false
        }
        super.onDestroy()
    }
}
