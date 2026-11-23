package cn.edu.sicnu.cs.stu.guolijie.musicplayer.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import cn.edu.sicnu.cs.stu.guolijie.musicplayer.R
import cn.edu.sicnu.cs.stu.guolijie.musicplayer.model.Song
import cn.edu.sicnu.cs.stu.guolijie.musicplayer.util.TimeUtils

/**
 * 歌曲列表适配器，点击某一项就通知 Activity 播放这首。
 */
class SongAdapter(
    private val songs: List<Song>,
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<SongAdapter.SongHolder>() {

    /** 正在播放的条目下标，-1 表示没有在播放 */
    var playingIndex = -1
        set(value) {
            val old = field
            field = value
            if (old >= 0) notifyItemChanged(old)
            if (value >= 0) notifyItemChanged(value)
        }

    inner class SongHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvIndex: TextView = itemView.findViewById(R.id.tvIndex)
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvArtist: TextView = itemView.findViewById(R.id.tvArtist)
        val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_song, parent, false)
        return SongHolder(view)
    }

    override fun onBindViewHolder(holder: SongHolder, position: Int) {
        val song = songs[position]
        holder.tvIndex.text = (position + 1).toString()
        holder.tvTitle.text = song.title
        holder.tvArtist.text = song.artist
        holder.tvDuration.text = TimeUtils.format(song.duration)
        // 正在播放的条目高亮（item_song 的 activated 状态）
        holder.itemView.isActivated = position == playingIndex
        holder.itemView.setOnClickListener { onItemClick(position) }
    }

    override fun getItemCount(): Int = songs.size
}
