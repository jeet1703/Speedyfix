package com.speedyfixautosales.store.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** What the AI is allowed to see: never an email, an order number or a registration. */
class ChatServiceTest {

    @Test
    void emailsOrderNumbersAndRegistrationsNeverReachTheAi() {
        ChatService.Masked m = ChatService.mask(List.of(
                new ChatService.Turn("assistant", "Hi! How can I help?"),
                new ChatService.Turn("user", "My car is AB12 CDE, email me at sam@example.com about SF-AB12CD34"),
                new ChatService.Turn("assistant", "That's [REG_1]."),
                new ChatService.Turn("user", "and my other car ab12cde, and XY65ZZZ")));

        String sent = m.turns().stream().map(ChatService.Turn::text).reduce("", String::concat);
        assertThat(sent).doesNotContain("AB12", "sam@example.com", "SF-AB12CD34", "XY65").contains("[REG_1]", "[REG_2]");
        // The same reg typed twice gets the same placeholder; the greeting before the shopper's first message is dropped.
        assertThat(m.regs()).containsEntry("[REG_1]", "AB12CDE").containsEntry("[REG_2]", "XY65ZZZ").hasSize(2);
        assertThat(m.turns().get(0).role()).isEqualTo("user");
        // The shopper's own reg comes back in the answer they see.
        assertThat(ChatService.unmask("That's [REG_1], a Nissan.", m.regs())).isEqualTo("That's AB12CDE, a Nissan.");
    }

    @Test
    void longConversationsAreCutToTheRecentTurns() {
        List<ChatService.Turn> many = java.util.stream.IntStream.range(0, 30)
                .mapToObj(i -> new ChatService.Turn(i % 2 == 0 ? "user" : "assistant", "x".repeat(900))).toList();
        ChatService.Masked m = ChatService.mask(many);
        assertThat(m.turns().size()).isLessThanOrEqualTo(ChatService.MAX_TURNS);
        assertThat(m.turns()).allSatisfy(t -> assertThat(t.text().length()).isLessThanOrEqualTo(ChatService.MAX_TEXT));
    }
}
