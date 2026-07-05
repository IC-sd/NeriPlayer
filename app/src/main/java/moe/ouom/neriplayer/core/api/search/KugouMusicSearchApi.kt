package moe.ouom.neriplayer.core.api.search

/*
 * NeriPlayer - A unified Android player for streaming music and videos from multiple online platforms.
 * Copyright (C) 2025-2025 NeriPlayer developers
 * https://github.com/cwuom/NeriPlayer
 *
 * This software is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this software.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * File: moe.ouom.neriplayer.core.api.search/KugouMusicSearchApi
 * Created: 2026/7/5
 */

import android.annotation.SuppressLint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import moe.ouom.neriplayer.BuildConfig
import moe.ouom.neriplayer.util.NPLogger
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import moe.ouom.neriplayer.core.di.AppContainer

@Serializable private data class KugouSearchResponse(
    val status: Int,
    val data: KugouSearchData? = null
)
@Serializable private data class KugouSearchData(val info: List<KugouSongSummary>? = null)
@Serializable private data class KugouSongSummary(
    val hash: String,
    val songname: String,
    val singername: String,
    @SerialName("album_name") val albumName: String?,
    @SerialName("album_id") val albumId: String?,
    val duration: Int? = 0
)

@Serializable private data class KugouSongInfoResponse(
    val status: Int,
    val data: KugouSongInfoData? = null
)
@Serializable private data class KugouSongInfoData(
    @SerialName("imgUrl") val imgUrl: String?,
    @SerialName("songName") val songName: String?,
    @SerialName("author_name") val authorName: String?,
    @SerialName("album_name") val albumName: String?,
    @SerialName("timeLength") val timeLength: Int? = 0
)

@Serializable private data class KugouLyricSearchResponse(
    val status: Int,
    val candidates: List<KugouLyricCandidate>? = null
)
@Serializable private data class KugouLyricCandidate(
    val id: String,
    val accesskey: String,
    val song: String?,
    val singer: String?,
    val duration: Int? = 0
)
@Serializable private data class KugouLyricDownloadResponse(
    val content: String? = null
)

class KugouMusicSearchApi : SearchApi {

    companion object {
        private const val TAG = "KugouMusicSearchApi"
        private const val DEBUG_JSON_PREVIEW_MAX_CHARS = 512
        private const val COVER_SIZE = "400"
    }

    private val client: OkHttpClient = AppContainer.sharedOkHttpClient
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(keyword: String, page: Int): List<SongSearchInfo> {
        return withContext(Dispatchers.IO) {
            val url = "http://mobilecdnbj.kugou.com/api/v3/search/song".toHttpUrl().newBuilder()
                .addQueryParameter("format", "json")
                .addQueryParameter("keyword", keyword)
                .addQueryParameter("page", page.toString())
                .addQueryParameter("pagesize", "20")
                .addQueryParameter("showtype", "1")
                .build()

            val responseJson = executeRequest(url.toString())
            logResponse(label = "kugou-search", responseJson = responseJson)

            val searchResult = json.decodeFromString<KugouSearchResponse>(responseJson)

            searchResult.data?.info?.map { song ->
                SongSearchInfo(
                    id = song.hash,
                    songName = song.songname,
                    singer = song.singername,
                    duration = formatDuration((song.duration ?: 0) / 1000),
                    source = MusicPlatform.KUGOU_MUSIC,
                    albumName = song.albumName,
                    coverUrl = null // 封面在 getSongInfo 中获取
                )
            } ?: emptyList()
        }
    }

    override suspend fun getSongInfo(id: String): SongDetails { // id is FileHash
        return withContext(Dispatchers.IO) {
            val url = "http://m.kugou.com/app/i/getSongInfo.php".toHttpUrl().newBuilder()
                .addQueryParameter("cmd", "playInfo")
                .addQueryParameter("hash", id)
                .build()

            val responseJson = executeRequest(url.toString())
            val infoJson = JSONObject(responseJson)

            val songName = infoJson.optString("songName", "未知歌曲")
            val authorName = infoJson.optString("author_name", "未知歌手")
            val albumName = infoJson.optString("album_name", "")
            val coverUrl = infoJson.optString("imgUrl", "")
                .replace("{size}", COVER_SIZE)
                .takeIf { it.isNotEmpty() }

            coroutineScope {
                val lyricDeferred = async { fetchKugouLyric(id) }

                val (lyric, translatedLyric) = lyricDeferred.await()
                SongDetails(
                    id = id,
                    songName = songName,
                    singer = authorName,
                    album = albumName,
                    coverUrl = coverUrl,
                    lyric = lyric,
                    translatedLyric = translatedLyric
                )
            }
        }
    }

    private fun fetchKugouLyric(hash: String): Pair<String?, String?> {
        return try {
            // Step 1: 搜索歌词
            val searchUrl = "http://lyrics.kugou.com/search".toHttpUrl().newBuilder()
                .addQueryParameter("ver", "1")
                .addQueryParameter("man", "yes")
                .addQueryParameter("client", "pc")
                .addQueryParameter("hash", hash)
                .build()

            val searchResponseJson = executeRequest(searchUrl.toString())
            val searchResult = json.decodeFromString<KugouLyricSearchResponse>(searchResponseJson)

            val candidate = searchResult.candidates?.firstOrNull()
                ?: return Pair(null, null)

            // Step 2: 下载歌词
            val downloadUrl = "http://lyrics.kugou.com/download".toHttpUrl().newBuilder()
                .addQueryParameter("ver", "1")
                .addQueryParameter("client", "pc")
                .addQueryParameter("id", candidate.id)
                .addQueryParameter("accesskey", candidate.accesskey)
                .addQueryParameter("fmt", "lrc")
                .build()

            val downloadResponseJson = executeRequest(downloadUrl.toString())
            val downloadResult = json.decodeFromString<KugouLyricDownloadResponse>(downloadResponseJson)

            val lyric = downloadResult.content?.let { content ->
                decodeBase64Content(content)
            }

            Pair(lyric, null)
        } catch (e: Exception) {
            NPLogger.e(TAG, "获取酷狗歌词失败", e)
            Pair(null, null)
        }
    }

    private fun decodeBase64Content(encoded: String): String? {
        return try {
            val decoded = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
            String(decoded, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private fun logResponse(label: String, responseJson: String) {
        val preview = responseJson
            .replace(Regex("\\s+"), " ")
            .take(DEBUG_JSON_PREVIEW_MAX_CHARS)
        if (BuildConfig.DEBUG) {
            NPLogger.d(TAG, "响应: label=$label, length=${responseJson.length}, preview=$preview")
            return
        }
        NPLogger.d(TAG, "响应: labelHash=${label.hashCode()}, length=${responseJson.length}")
    }

    @Throws(IOException::class)
    private fun executeRequest(url: String): String {
        val request = Request.Builder().url(url).build()
        return executeRequest(request)
    }

    @Throws(IOException::class)
    private fun executeRequest(request: Request): String {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("请求失败: ${response.code} for url: ${request.url}")
            return response.body?.string() ?: throw IOException("响应体为空")
        }
    }

    @SuppressLint("DefaultLocale")
    private fun formatDuration(seconds: Long): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%d:%02d", minutes, remainingSeconds)
    }
}
