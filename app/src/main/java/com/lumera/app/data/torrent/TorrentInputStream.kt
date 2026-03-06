package com.lumera.app.data.torrent

import android.util.Log
import com.lumera.app.BuildConfig
import org.libtorrent4j.Priority
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile

/**
 * Blocking InputStream that reads torrent file data from disk,
 * but only after verifying each piece is downloaded via havePiece().
 * Reads are clamped to piece boundaries to prevent reading undownloaded data.
 * Sets piece priorities and deadlines to keep download ahead of playback.
 */
class TorrentInputStream(
    file: File,
    private val stream: TorrentStream,
    startOffset: Long = 0L,
    length: Long = -1L
) : InputStream() {

    private val raf: RandomAccessFile
    private var position: Long = startOffset
    private var remaining: Long = if (length > 0) length else (stream.fileSize - startOffset)

    companion object {
        private const val TAG = "LumeraTorrent"
        private const val PIECE_WAIT_POLL_MS = 200L
        private const val PIECE_WAIT_TIMEOUT_MS = 90_000L
        private const val FILE_WAIT_TIMEOUT_MS = 60_000L
        private const val LOOKAHEAD = 15
        private const val DEADLINE_MS = 1000
    }

    init {
        // Wait for the file to exist on disk before opening
        val deadline = System.currentTimeMillis() + FILE_WAIT_TIMEOUT_MS
        while (!file.exists()) {
            if (System.currentTimeMillis() >= deadline) {
                throw IOException("Timeout waiting for torrent file to appear: ${file.name}")
            }
            Thread.sleep(PIECE_WAIT_POLL_MS)
        }
        raf = RandomAccessFile(file, "r")
        raf.seek(startOffset)
    }

    override fun read(): Int {
        if (remaining <= 0) return -1
        waitForPiece(position)
        val b = raf.read()
        if (b >= 0) {
            position++
            remaining--
        }
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (remaining <= 0) return -1

        // Clamp read to current piece boundary to avoid reading from an undownloaded piece
        val pieceIndex = stream.pieceIndexForOffset(position)
        val nextPieceStart = ((pieceIndex + 1).toLong() * stream.pieceLength) - stream.fileOffset
        val maxInPiece = (nextPieceStart - position).coerceAtLeast(1)
        val toRead = len.toLong().coerceAtMost(remaining).coerceAtMost(maxInPiece).toInt()

        waitForPiece(position)
        val bytesRead = raf.read(b, off, toRead)
        if (bytesRead > 0) {
            position += bytesRead
            remaining -= bytesRead
        }
        return bytesRead
    }

    override fun close() {
        raf.close()
    }

    /**
     * Block until the piece containing [fileOffset] is available on disk.
     * Also sets priorities and deadlines for lookahead pieces.
     */
    private fun waitForPiece(fileOffset: Long) {
        val pieceIndex = stream.pieceIndexForOffset(fileOffset)

        // Set lookahead priorities + deadlines
        for (i in 0 until LOOKAHEAD) {
            val idx = pieceIndex + i
            if (idx > stream.lastPiece) break
            try {
                if (!stream.handle.havePiece(idx)) {
                    stream.handle.piecePriority(idx, Priority.TOP_PRIORITY)
                    stream.handle.setPieceDeadline(idx, DEADLINE_MS * (i + 1))
                }
            } catch (e: Exception) {
                throw IOException("Torrent handle invalidated", e)
            }
        }

        // Already have it — fast path
        try {
            if (stream.handle.havePiece(pieceIndex)) return
        } catch (e: Exception) {
            throw IOException("Torrent handle invalidated", e)
        }

        if (BuildConfig.DEBUG) Log.d(TAG, "Waiting for piece $pieceIndex (offset=$fileOffset)")

        val deadline = System.currentTimeMillis() + PIECE_WAIT_TIMEOUT_MS
        while (true) {
            try {
                if (stream.handle.havePiece(pieceIndex)) return
            } catch (e: Exception) {
                throw IOException("Torrent handle invalidated", e)
            }
            if (System.currentTimeMillis() >= deadline) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Timeout waiting for piece $pieceIndex")
                throw IOException("Timeout waiting for torrent piece $pieceIndex")
            }
            Thread.sleep(PIECE_WAIT_POLL_MS)
        }
    }
}
