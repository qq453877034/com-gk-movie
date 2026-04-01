package com.gk.movie.Utils.Media3Play.ui

data class MovieInfo(
    val title: String = "",
    val coverUrl: String = "",
    val director: String = "",
    val actors: String = "",
    val types: String = "",
    val score: String = "",
    val scoreCount: String, 
    val description: String = "",
    val playLists: List<PlayList> = emptyList()
)

data class PlayList(
    val sourceName: String,
    val episodes: List<Episode>
)

data class Episode(
    val name: String,
    val url: String
)

sealed class UiState {
    object Loading : UiState()
    data class Success(val data: MovieInfo) : UiState()
    data class Error(val message: String) : UiState()
}