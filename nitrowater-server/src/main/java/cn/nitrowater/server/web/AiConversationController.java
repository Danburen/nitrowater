package cn.nitrowater.server.web;

import cn.nitrowater.lib.api.ApiResponse;
import cn.nitrowater.lib.utils.JsonUtil;
import cn.nitrowater.server.service.ai.model.DocSource;
import cn.nitrowater.server.service.ai.model.agent.ToolCall;
import cn.nitrowater.server.service.ai.ConversationService;
import cn.nitrowater.server.infra.entity.AiConversation;
import cn.nitrowater.server.infra.entity.AiMessage;
import cn.nitrowater.server.infra.enums.ConversationStatus;
import cn.nitrowater.server.web.dto.ConversationCreateReq;
import cn.nitrowater.server.web.dto.ConversationMessageResp;
import cn.nitrowater.server.web.dto.ConversationMessagesResp;
import cn.nitrowater.server.web.dto.ConversationPatchReq;
import cn.nitrowater.server.web.dto.ConversationResp;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;

import java.security.Principal;
import java.util.List;

/**
 * Conversation CRUD for NitroBot's server-side history.
 *
 * <ul>
 *   <li>{@code POST   /api/ai/conversations} — create an empty conversation;</li>
 *   <li>{@code GET    /api/ai/conversations} — cursor-paged list, newest first;</li>
 *   <li>{@code GET    /api/ai/conversations/{uuid}/messages} — page upwards with {@code before};</li>
 *   <li>{@code PATCH  /api/ai/conversations/{uuid}} — rename / archive;</li>
 *   <li>{@code DELETE /api/ai/conversations/{uuid}} — soft delete.</li>
 * </ul>
 *
 * <p>Every operation is scoped by the authenticated {@code uid}; another user's uuid is a 404.</p>
 */
@RestController
public class AiConversationController {

    private static final TypeReference<List<DocSource>> SOURCES_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<ToolCall>> TOOLS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> SKILLS_TYPE = new TypeReference<>() {
    };

    private final ConversationService conversations;

    public AiConversationController(ConversationService conversations) {
        this.conversations = conversations;
    }

    /** Login: create an empty conversation. */
    @PostMapping("/api/ai/conversations")
    public ConversationResp create(@Valid @RequestBody(required = false) ConversationCreateReq request,
            Principal principal) {
        long uid = CurrentUser.requireUid(principal);
        String title = request == null ? null : request.title();
        return toResp(conversations.create(uid, title));
    }

    /** Login: paged list of the user's conversations, newest first. */
    @GetMapping("/api/ai/conversations")
    public ApiResponse<Page<ConversationResp>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ConversationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Principal principal) {
        long uid = CurrentUser.requireUid(principal);
        return ApiResponse.success(conversations.list(uid, keyword, status, page, size)
                .map(AiConversationController::toResp));
    }

    /** Login: read one conversation's messages (newest page first, returned oldest-first). */
    @GetMapping("/api/ai/conversations/{uuid}/messages")
    public ConversationMessagesResp messages(@PathVariable String uuid,
            @RequestParam(required = false) Integer before,
            @RequestParam(required = false) Integer limit,
            Principal principal) {
        long uid = CurrentUser.requireUid(principal);
        ConversationService.MessagePage page = conversations.messages(uuid, uid, before, limit);
        return new ConversationMessagesResp(page.items().stream().map(AiConversationController::toResp).toList(),
                page.hasMore());
    }

    /** Login: rename or archive a conversation. */
    @PatchMapping("/api/ai/conversations/{uuid}")
    public ConversationResp update(@PathVariable String uuid, @Valid @RequestBody ConversationPatchReq request,
            Principal principal) {
        long uid = CurrentUser.requireUid(principal);
        return toResp(conversations.update(uuid, uid, request.title(), request.archived()));
    }

    /** Login: soft-delete a conversation. */
    @DeleteMapping("/api/ai/conversations/{uuid}")
    public void delete(@PathVariable String uuid, Principal principal) {
        conversations.delete(uuid, CurrentUser.requireUid(principal));
    }

    private static ConversationResp toResp(AiConversation conversation) {
        return new ConversationResp(
                conversation.getUuid(),
                conversation.getTitle(),
                conversation.getMessageCount(),
                conversation.getLastMessageAt(),
                conversation.getCreatedAt(),
                conversation.getStatus().name());
    }

    private static ConversationMessageResp toResp(AiMessage message) {
        return new ConversationMessageResp(
                message.getUuid(),
                message.getSeq(),
                message.getRole().name().toLowerCase(),
                message.getContent(),
                JsonUtil.parseList(message.getSourcesJson(), SOURCES_TYPE),
                JsonUtil.parseList(message.getToolsJson(), TOOLS_TYPE),
                JsonUtil.parseList(message.getSkillsJson(), SKILLS_TYPE),
                message.getModel(),
                Boolean.TRUE.equals(message.getModerated()),
                message.getCreatedAt());
    }

}
