package com.gk.movie.Utils.Media3Play.Util.cast

data class DlnaDevice(
    val name: String,
    val ip: String,
    // 用于播放、暂停、进度 (AVTransport)
    val avTransportUrl: String, 
    // 用于音量、静音 (RenderingControl)
    val renderingControlUrl: String? 
)