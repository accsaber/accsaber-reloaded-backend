package com.accsaber.backend.service.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.accsaber.backend.exception.ValidationException;

class BioSanitizerTest {

    private final BioSanitizer sanitizer = new BioSanitizer();

    @Test
    void stripsScriptTags() {
        String out = sanitizer.sanitize("<p>Hi</p><script>alert('x')</script>");
        assertThat(out).contains("<p>Hi</p>");
        assertThat(out).doesNotContain("<script");
        assertThat(out).doesNotContain("alert");
    }

    @Test
    void stripsImagesAndStyles() {
        String out = sanitizer.sanitize("<img src=x onerror=alert(1)><div style=\"color:red\">a</div>");
        assertThat(out).doesNotContain("<img");
        assertThat(out).doesNotContain("onerror");
        assertThat(out).doesNotContain("style=");
    }

    @Test
    void preservesAllowedFormatting() {
        String out = sanitizer.sanitize("<p><strong>bold</strong> and <em>italic</em></p>");
        assertThat(out).contains("<strong>bold</strong>");
        assertThat(out).contains("<em>italic</em>");
    }

    @Test
    void allowsLinksWithRelNofollow() {
        String out = sanitizer.sanitize("<a href=\"https://example.com\">link</a>");
        assertThat(out).contains("href=\"https://example.com\"");
        assertThat(out).contains("rel=\"nofollow\"");
    }

    @Test
    void dropsJavascriptUrls() {
        String out = sanitizer.sanitize("<a href=\"javascript:alert(1)\">bad</a>");
        assertThat(out).doesNotContain("javascript");
    }

    @Test
    void returnsEmptyForNull() {
        assertThat(sanitizer.sanitize(null)).isEmpty();
    }

    @Test
    void throwsWhenOverMaxLength() {
        String huge = "a".repeat(BioSanitizer.MAX_RAW_LENGTH + 1);
        assertThatThrownBy(() -> sanitizer.sanitize(huge))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void throwsWhenOverMaxLinks() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < BioSanitizer.MAX_LINKS + 1; i++) {
            sb.append("<a href=\"https://example.com/").append(i).append("\">l").append(i).append("</a> ");
        }
        assertThatThrownBy(() -> sanitizer.sanitize(sb.toString()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("links");
    }

    @Test
    void preservesTextAlignOnBlockElements() {
        String out = sanitizer.sanitize(
                "<p style=\"text-align: center\">c</p>"
                        + "<h3 style=\"text-align: right\">r</h3>"
                        + "<blockquote style=\"text-align:left\">l</blockquote>");
        assertThat(out).contains("text-align:center").contains("text-align:right").contains("text-align:left");
    }

    @Test
    void preservesAlignmentOnDivAndAllHeadings() {
        String out = sanitizer.sanitize(
                "<div style=\"text-align: center\">d</div>"
                        + "<h1 style=\"text-align: right\">h1</h1>"
                        + "<h2 style=\"text-align: left\">h2</h2>");
        assertThat(out).contains("<div").contains("text-align:center");
        assertThat(out).contains("<h1").contains("text-align:right");
        assertThat(out).contains("<h2").contains("text-align:left");
    }

    @Test
    void preservesLegacyAlignAttribute() {
        String out = sanitizer.sanitize(
                "<p align=\"center\">c</p><h3 align=\"right\">r</h3><div align=\"justify\">j</div>");
        assertThat(out).contains("align=\"center\"");
        assertThat(out).contains("align=\"right\"");
        assertThat(out).contains("align=\"justify\"");
    }

    @Test
    void dropsAlignAttributeWithJunkValue() {
        String out = sanitizer.sanitize("<p align=\"javascript:alert(1)\">x</p>");
        assertThat(out).doesNotContain("align=");
        assertThat(out).doesNotContain("javascript");
    }

    @Test
    void stripsNonTextAlignCssEvenOnAllowedElements() {
        String out = sanitizer.sanitize("<p style=\"color:red;text-align:center;font-size:99px\">x</p>");
        assertThat(out).contains("text-align:center");
        assertThat(out).doesNotContain("color");
        assertThat(out).doesNotContain("font-size");
    }

    @Test
    void allowsUpToMaxLinks() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < BioSanitizer.MAX_LINKS; i++) {
            sb.append("<a href=\"https://example.com/").append(i).append("\">l</a> ");
        }
        String out = sanitizer.sanitize(sb.toString());
        assertThat(out).contains("https://example.com/0");
    }
}
