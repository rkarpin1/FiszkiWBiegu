package pl.rkarpinski.fiszkiwbiegu

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@UnstableApi
class TtsPlayer : SimpleBasePlayer(Looper.getMainLooper()) {

    private var isPlayWhenReady = false
    private var currentMediaItem = MediaItem.Builder().setMediaId("empty").build()
    private var uid = 1L

    var onPlayWhenReadyChanged: ((Boolean) -> Unit)? = null
    var onSeekToNext: (() -> Unit)? = null
    var onSeekToPrevious: (() -> Unit)? = null

    override fun getState(): State = State.Builder()
        .setAvailableCommands(
            Player.Commands.Builder()
                .add(COMMAND_PLAY_PAUSE)
                .add(COMMAND_SEEK_TO_NEXT)
                .add(COMMAND_SEEK_TO_PREVIOUS)
                .add(COMMAND_STOP)
                .add(COMMAND_GET_CURRENT_MEDIA_ITEM)
                .build()
        )
        .setPlayWhenReady(isPlayWhenReady, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        .setPlaybackState(STATE_READY)
        .setPlaylist(
            ImmutableList.of(MediaItemData.Builder(uid).setMediaItem(currentMediaItem).build())
        )
        .setCurrentMediaItemIndex(0)
        .build()

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        isPlayWhenReady = playWhenReady
        onPlayWhenReadyChanged?.invoke(playWhenReady)
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        when (seekCommand) {
            COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                onSeekToNext?.invoke()
            }
            COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                onSeekToPrevious?.invoke()
            }
            else -> {}
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        isPlayWhenReady = false
        onPlayWhenReadyChanged?.invoke(false)
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()

    fun updateCurrentItem(mediaItem: MediaItem) {
        currentMediaItem = mediaItem
        uid = if (uid == Long.MAX_VALUE) 1L else uid + 1L
        invalidateState()
    }

    fun setPlaying(playing: Boolean) {
        if (isPlayWhenReady != playing) {
            isPlayWhenReady = playing
            invalidateState()
        }
    }

    fun refreshNotification() = invalidateState()
}
