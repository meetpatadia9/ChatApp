package com.ipsmeet.chatapp.dataclasses

data class MessagesDataClass(
    var key: String = "",
    val message: String = "",
    val imgURL: String = "",
    val senderID: String = "",
    val timeStamp: String = ""
)
