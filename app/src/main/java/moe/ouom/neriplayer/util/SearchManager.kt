package moe.ouom.neriplayer.util

import moe.ouom.neriplayer.core.api.search.MusicPlatform
import moe.ouom.neriplayer.core.api.search.SongSearchInfo
import moe.ouom.neriplayer.core.di.AppContainer

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
 * File: moe.ouom.neriplayer.util/SearchManager
 * Created: 2025/8/17
 */

object SearchManager {
    private val cloudMusicApi = AppContainer.cloudMusicSearchApi
    private val qqMusicApi = AppContainer.qqMusicSearchApi

    suspend fun search(
        keyword: String,
        platform: MusicPlatform,
    ): List<SongSearchInfo> {
        val api = if (platform == MusicPlatform.CLOUD_MUSIC) cloudMusicApi else qqMusicApi

        NPLogger.d("SearchManager", "try to search $keyword")
        return try {
            api.search(keyword, page = 1).take(10)
        } catch (e: Exception) {
            NPLogger.e("SearchManager", "Failed to find match", e)
            emptyList()
        }
    }

    suspend fun findBestMatch(
        songName: String,
        songArtist: String
    ): SongSearchInfo? {
        NPLogger.d("SearchManager", "try to search $songName")
        return try {
            val searchResults =
                qqMusicApi.search(songName, page = 1) + cloudMusicApi.search(songName, page = 1)
            val bestResult = searchResults.filter {
                it.songName == songName && it.singer == songArtist
            }
            val secondResult = searchResults.filter {
                it.songName == songName
            }
            if (bestResult.isNotEmpty()) {
                bestResult.first()
            } else {
                secondResult.firstOrNull()
            }
        } catch (e: Exception) {
            NPLogger.e("SearchManager", "Failed to find match", e)
            null
        }
    }
}