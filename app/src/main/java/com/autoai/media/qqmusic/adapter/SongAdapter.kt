package com.autoai.media.qqmusic.adapter

import com.autoai.media.qqmusic.R
import android.support.v4.media.MediaBrowserCompat
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.autoai.media.qqmusic.databinding.ItemSongBinding

internal class SongAdapter(private val onClick: (String) -> Unit) :
    ListAdapter<MediaBrowserCompat.MediaItem, SongAdapter.Holder>(object :
        DiffUtil.ItemCallback<MediaBrowserCompat.MediaItem>() {
        override fun areItemsTheSame(
            old: MediaBrowserCompat.MediaItem,
            new: MediaBrowserCompat.MediaItem
        ) = old.mediaId == new.mediaId

        override fun areContentsTheSame(
            old: MediaBrowserCompat.MediaItem,
            new: MediaBrowserCompat.MediaItem
        ): Boolean {
            val a = old.description
            val b = new.description
            return a.title == b.title && a.subtitle == b.subtitle && a.description == b.description &&
                    a.iconBitmap == b.iconBitmap && a.iconUri == b.iconUri
        }
    }) {
    private var currentMediaId: String? = null
    private var playbackStatus: String? = null

    /** 状态先保存再刷新行，列表异步返回或视图复用时也能显示正确状态。 */
    fun updatePlayback(mediaId: String?, status: String?) {
        if (currentMediaId == mediaId && playbackStatus == status) return
        val previousId = currentMediaId
        currentMediaId = mediaId
        playbackStatus = status
        listOfNotNull(previousId, mediaId).distinct().forEach { id ->
            val index = currentList.indexOfFirst { it.mediaId == id }
            if (index >= 0) notifyItemChanged(index)
        }
    }

    class Holder(val binding: ItemSongBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        holder.binding.apply {
            songTitle.text = item.description.title
            songSubtitle.text =
                listOfNotNull(item.description.subtitle, item.description.description).joinToString(
                    root.context.getString(R.string.metadata_separator)
                )
            songArtwork.setImageBitmap(item.description.iconBitmap)
            val status = playbackStatus.takeIf { item.mediaId == currentMediaId }
            songPlaybackStatus.text = status
            songPlaybackStatus.visibility = if (status == null) View.GONE else View.VISIBLE
            root.setOnClickListener { item.mediaId?.let(onClick) }
        }
    }
}
