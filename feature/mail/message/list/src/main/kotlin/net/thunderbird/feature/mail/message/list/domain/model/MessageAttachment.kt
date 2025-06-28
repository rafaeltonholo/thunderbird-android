package net.thunderbird.feature.mail.message.list.domain.model

data class MessageAttachment(
    val name: String,
    val type: Type,
) {
    enum class Type { Document, Image, Pdf, Other }
}
