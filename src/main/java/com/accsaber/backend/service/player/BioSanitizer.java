package com.accsaber.backend.service.player;

import java.util.regex.Pattern;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;

import com.accsaber.backend.exception.ValidationException;

@Component
public class BioSanitizer {

    public static final int MAX_RAW_LENGTH = 4000;
    public static final int MAX_LINKS = 5;

    private static final Pattern ANCHOR_OPEN = Pattern.compile("<a\\b", Pattern.CASE_INSENSITIVE);

    private static final PolicyFactory POLICY = new HtmlPolicyBuilder()
            .allowCommonInlineFormattingElements()
            .allowElements("p", "br", "ul", "ol", "li", "blockquote", "h3", "h4", "h5", "h6", "pre", "hr", "a")
            .allowUrlProtocols("http", "https")
            .allowAttributes("href").onElements("a")
            .requireRelNofollowOnLinks()
            .toFactory();

    public String sanitize(String html) {
        if (html == null) {
            return "";
        }
        if (html.length() > MAX_RAW_LENGTH) {
            throw new ValidationException("bio", "must not exceed " + MAX_RAW_LENGTH + " characters");
        }
        String sanitized = POLICY.sanitize(html);
        long linkCount = ANCHOR_OPEN.matcher(sanitized).results().count();
        if (linkCount > MAX_LINKS) {
            throw new ValidationException("bio", "must not contain more than " + MAX_LINKS + " links");
        }
        return sanitized;
    }
}
