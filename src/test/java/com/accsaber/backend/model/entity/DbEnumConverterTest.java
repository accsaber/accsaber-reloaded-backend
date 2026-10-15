package com.accsaber.backend.model.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.accsaber.backend.model.entity.campaign.CampaignCollaboratorStatusConverter;
import com.accsaber.backend.model.entity.campaign.CampaignCompletionModeConverter;
import com.accsaber.backend.model.entity.campaign.CampaignStatusConverter;
import com.accsaber.backend.model.entity.campaign.CampaignTagKindConverter;
import com.accsaber.backend.model.entity.campaign.CampaignVoteDirectionConverter;
import com.accsaber.backend.model.entity.campaign.UserCampaignStatusConverter;
import com.accsaber.backend.model.entity.map.BatchStatusConverter;
import com.accsaber.backend.model.entity.map.DifficultyConverter;
import com.accsaber.backend.model.entity.map.MapDifficultyStatusConverter;
import com.accsaber.backend.model.entity.map.MapVoteActionConverter;
import com.accsaber.backend.model.entity.map.VoteTypeConverter;
import com.accsaber.backend.model.entity.news.NewsStatusConverter;
import com.accsaber.backend.model.entity.staff.StaffRoleConverter;
import com.accsaber.backend.model.entity.staff.StaffUserStatusConverter;

import jakarta.persistence.AttributeConverter;

class DbEnumConverterTest {

    static Stream<Arguments> converters() {
        return Stream.of(
                Arguments.of(new AutoCriteriaStatusConverter(), new String[] { "pending", "passed", "failed", "unavailable" },
                        "Unknown auto criteria status: bogus"),
                Arguments.of(new CriteriaStatusConverter(), new String[] { "pending", "passed", "failed" },
                        "Unknown criteria status: bogus"),
                Arguments.of(new CampaignCollaboratorStatusConverter(), new String[] { "pending", "accepted", "declined" },
                        "Unknown campaign collaborator status: bogus"),
                Arguments.of(new CampaignCompletionModeConverter(), new String[] { "terminal", "all" },
                        "Unknown campaign completion mode: bogus"),
                Arguments.of(new CampaignStatusConverter(), new String[] { "draft", "published", "editing", "curated" },
                        "Unknown campaign status: bogus"),
                Arguments.of(new CampaignTagKindConverter(), new String[] { "category", "difficulty", "theme", "genre" },
                        "Unknown campaign tag kind: bogus"),
                Arguments.of(new CampaignVoteDirectionConverter(), new String[] { "up", "down" },
                        "Unknown campaign vote direction: bogus"),
                Arguments.of(new UserCampaignStatusConverter(), new String[] { "in_progress", "completed", "abandoned" },
                        "Unknown user campaign status: bogus"),
                Arguments.of(new BatchStatusConverter(), new String[] { "draft", "release_ready", "released" },
                        "Unknown batch status: bogus"),
                Arguments.of(new DifficultyConverter(), new String[] { "Easy", "Normal", "Hard", "Expert", "ExpertPlus" },
                        "Unknown difficulty: bogus"),
                Arguments.of(new MapDifficultyStatusConverter(), new String[] { "queue", "qualified", "ranked", "campaign" },
                        "Unknown map difficulty status: bogus"),
                Arguments.of(new MapVoteActionConverter(), new String[] { "rank", "reweight", "unrank" },
                        "Unknown map vote action: bogus"),
                Arguments.of(new VoteTypeConverter(), new String[] { "upvote", "downvote", "neutral" },
                        "Unknown vote type: bogus"),
                Arguments.of(new NewsStatusConverter(), new String[] { "DRAFT", "PUBLISHED", "ARCHIVED" },
                        "Unknown news status: bogus"),
                Arguments.of(new StaffRoleConverter(),
                        new String[] { "moderator", "ranking", "ranking_head", "developer", "campaign_curator",
                                "creative", "admin" },
                        "Unknown staff role: bogus"),
                Arguments.of(new StaffUserStatusConverter(), new String[] { "requested", "accepted", "denied" },
                        "Unknown staff user status: bogus"));
    }

    @ParameterizedTest
    @MethodSource("converters")
    <E extends Enum<E>> void roundTripsEveryConstantToTheSameDatabaseString(AttributeConverter<E, String> converter,
            String[] expected, String unknownMessage) {
        String[] dbValues = Arrays.stream(expected).map(converter::convertToEntityAttribute)
                .map(converter::convertToDatabaseColumn).toArray(String[]::new);
        E[] constants = converter.convertToEntityAttribute(expected[0]).getDeclaringClass().getEnumConstants();
        String[] written = Arrays.stream(constants).map(converter::convertToDatabaseColumn).toArray(String[]::new);

        assertThat(dbValues).containsExactly(expected);
        assertThat(written).containsExactly(expected);
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
        assertThatThrownBy(() -> converter.convertToEntityAttribute("bogus"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(unknownMessage);
    }
}
