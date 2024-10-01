package com.geeksville.mesh.ui.components

import android.text.Spannable
import android.text.Spannable.Factory
import android.text.style.URLSpan
import android.text.util.Linkify
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.text.util.LinkifyCompat
import com.geeksville.mesh.ui.theme.HyperlinkBlue

@Composable
internal fun AutoLinkText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
) {
    Text(
        text = autoLinkText(text),
        modifier = modifier,
        style = style,
    )
}

private fun autoLinkText(
    text: String,
): AnnotatedString = spannableToAnnotated(
    Factory.getInstance()
        .newSpannable(text)
        .also {
            LinkifyCompat.addLinks(
                it, Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES or Linkify.PHONE_NUMBERS
            )
        }
)

private fun spannableToAnnotated(spannable: Spannable): AnnotatedString = buildAnnotatedString {
    val linkSpanStyle = SpanStyle(
        color = HyperlinkBlue,
        textDecoration = TextDecoration.Underline,
    )
    var lastEnd = 0
    for (span in spannable.getSpans(0, spannable.length, Any::class.java)) {
        val start = spannable.getSpanStart(span)
        val end = spannable.getSpanEnd(span)
        append(spannable.subSequence(lastEnd, start))
        if (span is URLSpan) {
            withLink(
                LinkAnnotation.Url(
                    url = span.url,
                    styles = TextLinkStyles(style = linkSpanStyle)
                )
            ) {
                append(spannable.subSequence(start, end))
            }
        } else {
            append(spannable.subSequence(start, end))
        }
        lastEnd = end
    }
    append(spannable.subSequence(lastEnd, spannable.length))
}

@Preview(showBackground = true)
@Composable
private fun PreviewAutoLinkedText() {
    AutoLinkText("A text containing a link https://example.com")
}
