package com.accsaber.backend.service.player;

import java.util.List;
import java.util.regex.Pattern;

import org.owasp.html.CssSchema;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;

import com.accsaber.backend.exception.ValidationException;

@Component
public class RichTextSanitizer {

    public static final int MAX_LINKS = 5;

    private static final Pattern ANCHOR_OPEN = Pattern.compile("<a\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALIGN_VALUES = Pattern.compile("(?i)^(left|center|right|justify)$");
    private static final Pattern EFFECT_CLASSES = Pattern.compile("(?i)^(glow|outline|shadow)( (glow|outline|shadow))*$");

    private static final String[] BLOCK_ELEMENTS = {
            "p", "div", "blockquote", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li"
    };

    private static final CssSchema BASIC_CSS = CssSchema.withProperties(List.of("text-align"));

    private static final PolicyFactory RICH_POLICY = baseBuilder()
            .allowAttributes("class").matching(EFFECT_CLASSES).globally()
            .allowStyling(CssSchema.DEFAULT)
            .toFactory();

    private static final PolicyFactory BASIC_POLICY = baseBuilder()
            .allowStyling(BASIC_CSS)
            .toFactory();

    private static HtmlPolicyBuilder baseBuilder() {
        return new HtmlPolicyBuilder()
                .allowCommonInlineFormattingElements()
                .allowElements("p", "div", "br", "ul", "ol", "li", "blockquote",
                        "h1", "h2", "h3", "h4", "h5", "h6", "pre", "hr", "a", "span")
                .allowUrlProtocols("http", "https")
                .allowAttributes("href").onElements("a")
                .requireRelNofollowOnLinks()
                .allowAttributes("align").matching(ALIGN_VALUES).onElements(BLOCK_ELEMENTS);
    }

    public String sanitize(String html, int maxLength) {
        return sanitize(html, maxLength, true);
    }

    public String sanitize(String html, int maxLength, boolean allowRichEffects) {
        if (html == null) {
            return "";
        }
        if (html.length() > maxLength) {
            throw new ValidationException("content", "must not exceed " + maxLength + " characters");
        }
        PolicyFactory policy = allowRichEffects ? RICH_POLICY : BASIC_POLICY;
        String sanitized = policy.sanitize(html);
        long linkCount = ANCHOR_OPEN.matcher(sanitized).results().count();
        if (linkCount > MAX_LINKS) {
            throw new ValidationException("content", "must not contain more than " + MAX_LINKS + " links");
        }
        return sanitized;
    }
}
