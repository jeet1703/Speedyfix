package com.speedyfixautosales.store.chat;

import com.speedyfixautosales.store.common.BadRequestException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The help chat's AI answers (503 when the AI isn't switched on or is busy: the chat then answers by itself). */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chat;

    public ChatController(ChatService chat) {
        this.chat = chat;
    }

    public record ChatRequest(@NotNull @Size(min = 1, max = 40) List<ChatService.Turn> messages, @Size(max = 80) String vehicleId) {
    }

    @PostMapping
    public ChatService.Reply reply(@Valid @RequestBody ChatRequest request) {
        if (request.messages().isEmpty() || !"user".equals(request.messages().get(request.messages().size() - 1).role())) {
            throw new BadRequestException("Ask a question.");
        }
        return chat.reply(request.messages(), request.vehicleId());
    }
}
