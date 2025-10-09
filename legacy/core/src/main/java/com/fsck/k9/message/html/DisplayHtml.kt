package com.fsck.k9.message.html

import app.k9mail.html.cleaner.HtmlHeadProvider
import org.intellij.lang.annotations.Language

class DisplayHtml(private val settings: HtmlSettings) : HtmlHeadProvider {
    override val headHtml: String
        get() {
            @Language("HTML")
            val html = """
                <meta name="viewport" content="width=device-width, initial-scale=1, minimum-scale=1, maximum-scale=2">
                ${cssStyleGlobal()}
                ${cssStylePre()}
                ${cssStyleSignature()}
            """.trimIndent()

            return html
        }

    fun wrapStatusMessage(status: CharSequence): String {
        @Language("HTML")
        val html = """
            <div style="text-align:center; color: grey;">$status</div>
        """.trimIndent()

        return wrapMessageContent(html)
    }

    @Language("HTML")
    fun wrapMessageContent(messageContent: CharSequence): String {
        // Include a meta tag so the WebView will not use a fixed viewport width of 980 px
        return """
            <html dir="auto">
                <head>
                    $headHtml
                </head>
                <body>
                    <div class="message message-content">
                        <div class="clear">
                            $messageContent
                        </div>
                    </div>
                </body>
            </html>
        """.trimIndent()
    }

    /**
     * Dynamically generates a CSS style block that applies global rules to all elements (`*` selector).
     *
     * The style enforces word-breaking and overflow wrapping to prevent content overflow
     * and ensures long text strings break correctly without causing horizontal scrolling.
     *
     * @return A `<style>` element string that can be dynamically injected into the HTML `<head>`
     * to apply these global styles when rendering messages.
     */
    @Language("HTML")
    private fun cssStyleGlobal(): String {
        return """
            <style type="text/css">
                body { font-size: 0.9rem; }
                .clear:after {
                  content: "";
                  clear: both;
                  display: block;
                }
                .message {
                  display: block;
                  user-select: auto;
                  -webkit-user-select: auto;
                }
                .message.message-content {
                  width: 100%;
                  overflow-wrap: break-word;
                }
                .message.message-content pre { white-space: pre-wrap; }
                .message.message-content blockquote {
                  margin-left: .8ex !important;
                  margin-right: 0 !important;
                  border-left: 1px #ccc solid !important;
                  padding-left: 1ex !important
                }
                .message.message-content pre,
                .message.message-content code,
                .message.message-content table {
                  max-width: 100%;
                  overflow-x: auto;
                }
                body, div, section, article, main, header, footer {
                  overflow-x: hidden;
                }
                div.table-wrapper:has(table) {
                  overflow-x: auto;
                  display: block;
                  width: 100% !important;
                }
            </style>
        """.trimIndent()
    }

    /**
     * Dynamically generate a CSS style for `<pre>` elements.
     *
     * The style incorporates the user's current preference setting for the font family used for plain text messages.
     *
     * @return A `<style>` element that can be dynamically included in the HTML `<head>` element when messages are
     * displayed.
     */
    @Language("HTML")
    private fun cssStylePre(): String {
        val font = if (settings.useFixedWidthFont) "monospace" else "sans-serif"

        return """
            <style type="text/css">
                pre.${EmailTextToHtml.K9MAIL_CSS_CLASS} {
                    white-space: pre-wrap;
                    word-wrap: break-word;
                    font-family: $font;
                    margin-top: 0px;
                }
            </style>
        """.trimIndent()
    }

    @Language("HTML")
    private fun cssStyleSignature(): String {
        return """
            <style type="text/css">
                .k9mail-signature {
                    opacity: 0.5;
                }
            </style>
        """.trimIndent()
    }
}
