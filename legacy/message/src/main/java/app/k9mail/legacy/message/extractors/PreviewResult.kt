package app.k9mail.legacy.message.extractors

class PreviewResult private constructor(
    val previewType: PreviewType,
    private val _previewText: String?,
) {
    val isPreviewTextAvailable: Boolean
        get() = previewType == PreviewType.TEXT

    val previewText: String
        get() {
            check(isPreviewTextAvailable) { "Preview is not available" }
            checkNotNull(_previewText) { "Preview is not available" }

            return _previewText
        }

    enum class PreviewType {
        NONE,
        TEXT,
        ENCRYPTED,
        ERROR
    }

    companion object {
        @JvmStatic
        fun text(previewText: String): PreviewResult {
            return PreviewResult(PreviewType.TEXT, previewText)
        }

        fun encrypted(): PreviewResult {
            return PreviewResult(PreviewType.ENCRYPTED, null)
        }

        @JvmStatic
        fun none(): PreviewResult {
            return PreviewResult(PreviewType.NONE, null)
        }

        @JvmStatic
        fun error(): PreviewResult {
            return PreviewResult(PreviewType.ERROR, null)
        }
    }
}
