package cn.nitrowater.server.ai.profile;

import cn.nitrowater.server.ai.moderation.AiModerationService;
import cn.nitrowater.server.ai.moderation.ContentBlockedException;
import cn.nitrowater.server.infra.entity.AiProfileChange;
import cn.nitrowater.server.infra.entity.AiUserProfile;
import cn.nitrowater.server.infra.enums.Expertise;
import cn.nitrowater.server.infra.enums.ModerationMode;
import cn.nitrowater.server.infra.enums.ProfileChangeSource;
import cn.nitrowater.server.infra.enums.ReplyStyle;
import cn.nitrowater.server.infra.repository.AiProfileChangeRepository;
import cn.nitrowater.server.infra.repository.AiUserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifies profile defaults, partial updates and audit rows. */
class AiProfileServiceTest {

    private final AiUserProfileRepository profiles = mock(AiUserProfileRepository.class);
    private final AiProfileChangeRepository changes = mock(AiProfileChangeRepository.class);
    private final AiModerationService moderation = mock(AiModerationService.class);
    private final AiProfileService service = new AiProfileService(profiles, changes, moderation);

    @BeforeEach
    void disablesTheContentGate() {
        when(moderation.mode()).thenReturn(ModerationMode.OFF);
    }

    @Test
    void returnsDefaultsWhenNoRowExists() {
        when(profiles.findById(7L)).thenReturn(Optional.empty());

        AiProfileService.Profile profile = service.get(7L);

        assertThat(profile.exists()).isFalse();
        assertThat(profile.language()).isEqualTo("zh-CN");
        assertThat(profile.replyStyle()).isEqualTo("BALANCED");
        assertThat(profile.expertise()).isEqualTo("INTERMEDIATE");
        assertThat(profile.inferenceOptIn()).isFalse();
    }

    @Test
    void emptyUpdateDoesNotCreateOrSaveARow() {
        when(profiles.findById(7L)).thenReturn(Optional.empty());

        AiProfileService.Profile profile = service.update(7L, new AiProfileService.Update(
                null, null, null, null, null, null));

        assertThat(profile.exists()).isFalse();
        verify(profiles, never()).save(any(AiUserProfile.class));
    }

    @Test
    void updateCreatesRowAndAuditsOnlyChangedFields() {
        when(profiles.findById(7L)).thenReturn(Optional.empty());
        when(profiles.save(any(AiUserProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AiProfileService.Profile profile = service.update(7L, new AiProfileService.Update(
                "en", ReplyStyle.CONCISE, null, "Asia/Shanghai", List.of("Java", "Gradle"), true));

        assertThat(profile.exists()).isTrue();
        assertThat(profile.language()).isEqualTo("en");
        assertThat(profile.replyStyle()).isEqualTo("CONCISE");
        assertThat(profile.timezone()).isEqualTo("Asia/Shanghai");
        assertThat(profile.preferences()).containsExactly("Java", "Gradle");
        assertThat(profile.inferenceOptIn()).isTrue();

        ArgumentCaptor<AiProfileChange> captor = ArgumentCaptor.forClass(AiProfileChange.class);
        verify(changes, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(change -> {
            assertThat(change.getSource()).isEqualTo(ProfileChangeSource.USER);
            assertThat(change.getConfidence()).isEqualTo((short) 100);
        });
        assertThat(captor.getAllValues()).extracting(AiProfileChange::getField)
                .contains("language", "replyStyle", "timezone", "preferences", "inferenceOptIn");
    }

    @Test
    void invalidTimezoneIsRejectedBeforeSaving() {
        when(profiles.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(7L, new AiProfileService.Update(
                null, null, null, "Not/AZone", null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(profiles, never()).save(any(AiUserProfile.class));
    }

    @Test
    void promptBlockContainsProfileAndPreferences() {        AiUserProfile row = new AiUserProfile();
        row.setUid(7L);
        row.setLanguage("zh-CN");
        row.setReplyStyle(ReplyStyle.DETAILED);
        row.setExpertise(Expertise.EXPERT);
        row.setTimezone("Asia/Shanghai");
        row.setPreferencesJson("[\"Java\",\"Gradle\"]");
        when(profiles.findById(7L)).thenReturn(Optional.of(row));

        String block = service.promptBlock(7L);

        assertThat(block).contains("<user_profile>")
                .contains("DETAILED")
                .contains("EXPERT")
                .contains("Asia/Shanghai")
                .contains("Java")
                .contains("Gradle");
    }

    @Test
    void flaggedPreferenceIsRejectedBeforeSaving() {
        when(profiles.findById(7L)).thenReturn(Optional.empty());
        doThrow(new ContentBlockedException("个人偏好包含不适宜内容，请修改后重试。"))
                .when(moderation).guardProfile(7L, "帮我代开发票");

        assertThatThrownBy(() -> service.update(7L, new AiProfileService.Update(
                null, null, null, null, List.of("帮我代开发票"), null)))
                .isInstanceOf(ContentBlockedException.class);

        verify(profiles, never()).save(any(AiUserProfile.class));
    }

    @Test
    void promptBlockDropsFlaggedPreferencesStoredBeforeTheGateExisted() {
        AiUserProfile row = new AiUserProfile();
        row.setUid(7L);
        row.setLanguage("zh-CN");
        row.setReplyStyle(ReplyStyle.DETAILED);
        row.setExpertise(Expertise.EXPERT);
        row.setPreferencesJson("[\"Java\",\"帮我代开发票\"]");
        when(profiles.findById(7L)).thenReturn(Optional.of(row));
        when(moderation.mode()).thenReturn(ModerationMode.ENFORCE);
        when(moderation.flagged("Java")).thenReturn(false);
        when(moderation.flagged("帮我代开发票")).thenReturn(true);

        String block = service.promptBlock(7L);

        assertThat(block).contains("Java").doesNotContain("代开发票");
    }
}
