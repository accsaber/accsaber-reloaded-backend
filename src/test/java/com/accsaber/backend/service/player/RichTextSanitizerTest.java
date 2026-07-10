package com.accsaber.backend.service.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.accsaber.backend.exception.ValidationException;

class RichTextSanitizerTest {

    private static final int MAX = 4000;

    private final RichTextSanitizer sanitizer = new RichTextSanitizer();

    @Test
    void stripsScriptTags() {
        String out = sanitizer.sanitize("<p>Hi</p><script>alert('x')</script>", MAX);
        assertThat(out).contains("<p>Hi</p>");
        assertThat(out).doesNotContain("<script");
        assertThat(out).doesNotContain("alert");
    }

    @Test
    void stripsImagesAndEventHandlers() {
        String out = sanitizer.sanitize("<img src=x onerror=alert(1)><div>a</div>", MAX);
        assertThat(out).doesNotContain("<img");
        assertThat(out).doesNotContain("onerror");
    }

    @Test
    void preservesAllowedFormatting() {
        String out = sanitizer.sanitize("<p><strong>bold</strong> and <em>italic</em></p>", MAX);
        assertThat(out).contains("<strong>bold</strong>");
        assertThat(out).contains("<em>italic</em>");
    }

    @Test
    void preservesRgbColor() {
        String out = sanitizer.sanitize("<span style=\"color: rgb(168, 85, 247);\">4444</span>", MAX);
        assertThat(out).contains("rgb(").contains("168").contains("247").contains("4444");
    }

    @Test
    void allowsColorAndFontStyling() {
        String out = sanitizer.sanitize(
                "<span style=\"color:#ff0000;font-size:24px;font-family:Arial;font-weight:bold\">x</span>", MAX);
        assertThat(out).contains("color");
        assertThat(out).contains("font-size");
        assertThat(out).contains("font-family");
    }

    @Test
    void allowsWhitelistedEffectClass() {
        String out = sanitizer.sanitize("<span class=\"glow\">x</span>", MAX);
        assertThat(out).contains("class=\"glow\"");
    }

    @Test
    void stripsUnknownClass() {
        String out = sanitizer.sanitize("<span class=\"evilthing\">x</span>", MAX);
        assertThat(out).doesNotContain("evilthing");
    }

    @Test
    void stripsDangerousCssProperties() {
        String out = sanitizer.sanitize(
                "<p style=\"color:red;position:absolute;background-image:url(x)\">x</p>", MAX);
        assertThat(out).contains("color");
        assertThat(out).doesNotContain("position");
        assertThat(out).doesNotContain("background-image");
    }

    @Test
    void allowsLinksWithRelNofollow() {
        String out = sanitizer.sanitize("<a href=\"https://example.com\">link</a>", MAX);
        assertThat(out).contains("href=\"https://example.com\"");
        assertThat(out).contains("rel=\"nofollow\"");
    }

    @Test
    void dropsJavascriptUrls() {
        String out = sanitizer.sanitize("<a href=\"javascript:alert(1)\">bad</a>", MAX);
        assertThat(out).doesNotContain("javascript");
    }

    @Test
    void returnsEmptyForNull() {
        assertThat(sanitizer.sanitize(null, MAX)).isEmpty();
    }

    @Test
    void throwsWhenOverMaxLength() {
        String huge = "a".repeat(MAX + 1);
        assertThatThrownBy(() -> sanitizer.sanitize(huge, MAX))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void throwsWhenOverMaxLinks() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < RichTextSanitizer.MAX_LINKS + 1; i++) {
            sb.append("<a href=\"https://example.com/").append(i).append("\">l").append(i).append("</a> ");
        }
        assertThatThrownBy(() -> sanitizer.sanitize(sb.toString(), MAX))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("links");
    }

    @Test
    void preservesTextAlignOnBlockElements() {
        String out = sanitizer.sanitize("<p style=\"text-align: center\">c</p>", MAX);
        assertThat(out).contains("text-align:center");
    }

    @Test
    void preservesLegacyAlignAttribute() {
        String out = sanitizer.sanitize("<p align=\"center\">c</p>", MAX);
        assertThat(out).contains("align=\"center\"");
    }

    @Test
    void dropsAlignAttributeWithJunkValue() {
        String out = sanitizer.sanitize("<p align=\"javascript:alert(1)\">x</p>", MAX);
        assertThat(out).doesNotContain("align=");
        assertThat(out).doesNotContain("javascript");
    }

    @Test
    void basicPolicyStripsColorAndFontStyling() {
        String out = sanitizer.sanitize(
                "<span style=\"color:#ff0000;font-size:24px;font-family:Arial\">x</span>", MAX, false);
        assertThat(out).doesNotContain("color");
        assertThat(out).doesNotContain("font-size");
        assertThat(out).doesNotContain("font-family");
        assertThat(out).contains("x");
    }

    @Test
    void basicPolicyStripsTextShadow() {
        String out = sanitizer.sanitize("<span style=\"text-shadow:0 0 5px red\">x</span>", MAX, false);
        assertThat(out).doesNotContain("text-shadow");
        assertThat(out).contains("x");
    }

    @Test
    void basicPolicyStripsEffectClasses() {
        String out = sanitizer.sanitize("<span class=\"glow\">x</span>", MAX, false);
        assertThat(out).doesNotContain("glow");
        assertThat(out).contains("x");
    }

    @Test
    void basicPolicyPreservesFormattingAndAlignment() {
        String out = sanitizer.sanitize(
                "<p style=\"text-align:center\"><strong>hi</strong> and <em>there</em></p>", MAX, false);
        assertThat(out).contains("text-align:center");
        assertThat(out).contains("<strong>hi</strong>");
        assertThat(out).contains("<em>there</em>");
    }
}
