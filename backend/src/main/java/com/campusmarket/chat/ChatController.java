package com.campusmarket.chat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.campusmarket.item.MarketItemService;
import com.campusmarket.security.MarketPrincipal;

@RestController
@RequestMapping("/api/conversations")
public class ChatController {
    private final ChatMapper chat;
    private final MarketItemService items;
    private final ApplicationEventPublisher events;

    public ChatController(ChatMapper chat, MarketItemService items, ApplicationEventPublisher events) {
        this.chat = chat;
        this.items = items;
        this.events = events;
    }

    @GetMapping
    public List<ChatMapper.Conversation> list(@AuthenticationPrincipal MarketPrincipal account) {
        return chat.list(account.id());
    }

    @PostMapping
    @Transactional
    public Map<String,Long> open(@AuthenticationPrincipal MarketPrincipal account, @RequestBody OpenInput input) {
        var item = items.publicItem(input.itemId());
        MarketItemService.requireState(item.sellerId() != account.id(), "不能联系自己。");
        chat.open(item.id(), account.id());
        return Map.of("id", chat.findId(item.id(), account.id()));
    }

    @GetMapping("/{id}/messages")
    public List<ChatMapper.Message> messages(@AuthenticationPrincipal MarketPrincipal account,
                                             @PathVariable long id, @RequestParam(required = false) Long before,
                                             @RequestParam(required = false) Long after) {
        membership(account.id(), id);
        if (after != null) {
            return chat.messagesAfter(id, after);
        }
        return chat.messages(id, before == null ? Long.MAX_VALUE : before);
    }

    @PostMapping("/{id}/messages")
    @Transactional
    public ChatMapper.Message send(@AuthenticationPrincipal MarketPrincipal account,
                                   @PathVariable long id, @RequestBody SendInput input) {
        var conversation = membership(account.id(), id);
        MarketItemService.requireText(input.body(), 2000, "请输入 1 至 2000 字的消息。");
        if (input.requestId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少消息编号。");
        }
        String key = input.requestId().toString();
        chat.lockConversation(id);
        chat.insert(id, account.id(), key, input.body().strip());
        var message = chat.findMessage(id, account.id(), key);
        MarketItemService.requireState(message.body().equals(input.body().strip()), "该消息编号已被使用。");
        chat.touch(id, message.createdAt());
        events.publishEvent(new ChatChanged(conversation.buyerId(), conversation.sellerId(), id));
        return message;
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@AuthenticationPrincipal MarketPrincipal account, @PathVariable long id, @RequestBody ReadInput input) {
        membership(account.id(), id);
        chat.read(id, account.id(), input.messageId());
    }

    private ChatMapper.Conversation membership(long user, long id) {
        var member = chat.member(id, user);
        if (member == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在。");
        }
        return member;
    }

    public record OpenInput(long itemId) {}
    public record SendInput(UUID requestId, String body) {}
    public record ReadInput(long messageId) {}
    public record ChatChanged(long buyerId, long sellerId, long conversationId) {}
}
