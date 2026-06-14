package com.akshat.ai.help_desk_bot.controller;


import com.akshat.ai.help_desk_bot.entity.ChatMessage;
import com.akshat.ai.help_desk_bot.entity.Conversation;
import com.akshat.ai.help_desk_bot.repository.ChatMessageRepository;
import com.akshat.ai.help_desk_bot.security.JwtUserContext;
import com.akshat.ai.help_desk_bot.service.ConversationService;
import com.akshat.ai.help_desk_bot.tools.BotTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/ai")
public class BotController {

    private static final Logger log = LoggerFactory.getLogger(BotController.class);

    @Autowired
    @Qualifier("BotChatClient")
    private ChatClient chatClient;

    @Autowired
    private BotTools botTools;

    @Value("classpath:/helpdesk-system.st")
    private Resource resource;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private JwtUserContext jwtUserContext;

    @PostMapping("/chat")
    public ChatResponse generateResponse(
            @RequestParam("prompt") String query,
            @RequestHeader(value = "ConversationId", required = false) String conversationId,
            @AuthenticationPrincipal Jwt jwt) {

        if (conversationId == null || conversationId.isBlank()) {
            conversationId = UUID.randomUUID().toString();
            log.debug("No ConversationId provided; generated={}", conversationId);
        }

        String username = jwtUserContext.getUsername(jwt);
        String fullName = jwtUserContext.getFullName(jwt);
        String email = jwtUserContext.getEmail(jwt);

        log.info("Chat request: conversationId='{}' username='{}' prompt='{}'", conversationId, username, query);

        botTools.registerEmail(username, email);
        conversationService.upsertConversation(conversationId, username);

        final String cid = conversationId;

        try {
            // Inject identity into system prompt only — never into user message
            String baseSystem = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String fullSystem = "Employee username: " + username + ". Full name: " + fullName + ".\n\n" + baseSystem;

            String result = chatClient
                    .prompt()
                    .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, cid))
                    .tools(botTools)
                    .system(fullSystem)  // system prompt has identity
                    .user(query)         // user message only
                    .call()
                    .content();

            log.info("Chat response: conversationId='{}' result='{}'", cid, result);
            return new ChatResponse(cid, result);

        } catch (Exception e) {
            log.error("Chat request failed for conversationId='{}'", cid, e);
            return new ChatResponse(cid, "Something went wrong. Please try again.");
        }
    }

    // Debug endpoint to inspect conversation messages persisted in DB
    @GetMapping("/memory")
    public List<String> getMemory(@RequestHeader("ConversationId") String conversationId) {
        List<ChatMessage> list = chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        return list.stream().map(ChatMessage::getContent).collect(Collectors.toList());
    }

    // Debug endpoint to directly write a chat message into the DB for the given conversation (test DB connectivity)
    @PostMapping("/memory/push")
    public ChatMessage pushMemory(@RequestHeader(value = "ConversationId", required = false) String conversationId,
                                  @RequestBody String content) {
        if (conversationId == null || conversationId.trim().isEmpty()) {
            conversationId = UUID.randomUUID().toString();
            log.debug("No ConversationId header supplied for push; generated conversationId={}", conversationId);
        }
        ChatMessage cm = new ChatMessage();
        cm.setConversationId(conversationId);
        cm.setContent(content == null ? "" : content);
        ChatMessage saved = chatMessageRepository.save(cm);
        log.debug("Pushed chat message id={} conversationId={}", saved.getId(), conversationId);
        return saved;
    }

    // Debug endpoint to clear conversation memory (DB)
    @DeleteMapping("/memory")
    public void clearMemory(@RequestHeader("ConversationId") String conversationId) {
        chatMessageRepository.deleteByConversationId(conversationId);
        log.debug("Cleared messages for conversationId={}", conversationId);
    }

    // Get all conversations for a user
    @GetMapping("/conversations")
    public Object getConversations(@AuthenticationPrincipal Jwt jwt) {
        String username = jwtUserContext.getUsername(jwt);
        return conversationService.getConversationsByUsername(username);
    }


    // Get all messages for a specific conversation
    @GetMapping("/conversations/{conversationId}/messages")
    public List<String> getConversationMessages(@PathVariable String conversationId) {
        return chatMessageRepository
                .findByConversationIdOrderByCreatedAtAsc(conversationId)
                .stream()
                .map(ChatMessage::getContent)
                .toList();
    }

    @GetMapping("/me")
    public Object getMe(@AuthenticationPrincipal Jwt jwt) {
        return java.util.Map.of(
                "username", jwtUserContext.getUsername(jwt),
                "email", jwtUserContext.getEmail(jwt),
                "name", jwtUserContext.getFullName(jwt)
        );
    }
}
