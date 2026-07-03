package com.accsaber.backend.service.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextModerationServiceTest {

    private final TextModerationService service = new TextModerationService();

    @Test
    void cleanTextPasses() {
        assertThat(service.isClean("Let's design a great accuracy campaign together")).isTrue();
    }

    @Test
    void nullAndBlankPass() {
        assertThat(service.isClean(null)).isTrue();
        assertThat(service.isClean("   ")).isTrue();
    }

    @Test
    void generalProfanityIsAllowed() {
        assertThat(service.isClean("this map is fucking hard")).isTrue();
        assertThat(service.isClean("what a shit design")).isTrue();
        assertThat(service.isClean("damn this bitch of a jump is annoying")).isTrue();
    }

    @Test
    void discriminatorySlursAreBlocked() {
        assertThat(service.isClean("you faggot")).isFalse();
        assertThat(service.isClean("stop being such a retard")).isFalse();
    }

    @Test
    void leetspeakSlursAreBlocked() {
        assertThat(service.isClean("total f@ggot move")).isFalse();
        assertThat(service.isClean("you f4g")).isFalse();
    }

    @Test
    void spacedAndSeparatedSlurEvasionIsBlocked() {
        assertThat(service.isClean("what a f a g g o t")).isFalse();
        assertThat(service.isClean("f.a.g")).isFalse();
    }

    @Test
    void repeatedCharacterSlurEvasionIsBlocked() {
        assertThat(service.isClean("faaaggot")).isFalse();
    }

    @Test
    void unicodeHomoglyphSlurEvasionIsBlocked() {
        assertThat(service.isClean("you fаggot")).isFalse();
    }

    @Test
    void slurInsideHtmlContentIsBlocked() {
        assertThat(service.isClean("<span style=\"color:red\">faggot</span>")).isFalse();
        assertThat(service.isClean("<p>hello world</p>")).isTrue();
    }

    @Test
    void innocentWordsContainingBlockedSubstringsPass() {
        assertThat(service.isClean("the class assignment for the assassin in the cockpit")).isTrue();
        assertThat(service.isClean("pass the glass, assess the assets")).isTrue();
    }

    @Test
    void acronymsAndSpelledInitialsPass() {
        assertThat(service.isClean("check the F A Q and the R U S H sections")).isTrue();
    }
}
