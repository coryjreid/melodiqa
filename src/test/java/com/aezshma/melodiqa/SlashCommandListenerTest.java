package com.aezshma.melodiqa;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SlashCommandListenerTest {

    @Mock private BotController mockController;
    @Mock private SlashCommandInteractionEvent mockEvent;
    @Mock private Member mockMember;
    @Mock private GuildVoiceState mockVoiceState;
    @Mock private AudioChannelUnion mockChannel;
    @Mock private Guild mockGuild;
    @Mock private ReplyCallbackAction mockReplyAction;

    private SlashCommandListener listener;

    @BeforeEach
    void setUp() {
        listener = new SlashCommandListener(mockController);
        lenient().when(mockEvent.reply(anyString())).thenReturn(mockReplyAction);
        lenient().when(mockReplyAction.setEphemeral(anyBoolean())).thenReturn(mockReplyAction);
    }

    // --- /join tests ---

    @Test
    void joinWhenUserNotInVoiceChannelRepliesWithError() {
        when(mockEvent.getName()).thenReturn("join");
        when(mockEvent.getMember()).thenReturn(mockMember);
        when(mockMember.getVoiceState()).thenReturn(mockVoiceState);
        when(mockVoiceState.inAudioChannel()).thenReturn(false);

        listener.onSlashCommandInteraction(mockEvent);

        verify(mockEvent).reply("You must be in a voice channel to use this command.");
        verify(mockReplyAction).setEphemeral(true);
        verify(mockReplyAction).queue();
        verifyNoInteractions(mockController);
    }

    @Test
    void joinWhenBotAlreadyInThisGuildRepliesWithError() {
        when(mockEvent.getName()).thenReturn("join");
        when(mockEvent.getMember()).thenReturn(mockMember);
        when(mockMember.getVoiceState()).thenReturn(mockVoiceState);
        when(mockVoiceState.inAudioChannel()).thenReturn(true);
        when(mockEvent.getGuild()).thenReturn(mockGuild);
        when(mockGuild.getIdLong()).thenReturn(111L);
        when(mockController.getConnectedGuildId()).thenReturn(111L);

        listener.onSlashCommandInteraction(mockEvent);

        verify(mockEvent).reply("I'm already in a voice channel. Use `/leave` first.");
        verify(mockReplyAction).setEphemeral(true);
        verify(mockReplyAction).queue();
        verify(mockController, never()).join(any());
    }

    @Test
    void joinWhenBotBusyInAnotherGuildRepliesWithError() {
        when(mockEvent.getName()).thenReturn("join");
        when(mockEvent.getMember()).thenReturn(mockMember);
        when(mockMember.getVoiceState()).thenReturn(mockVoiceState);
        when(mockVoiceState.inAudioChannel()).thenReturn(true);
        when(mockEvent.getGuild()).thenReturn(mockGuild);
        when(mockGuild.getIdLong()).thenReturn(222L);
        when(mockController.getConnectedGuildId()).thenReturn(111L);

        listener.onSlashCommandInteraction(mockEvent);

        verify(mockEvent).reply("I'm currently in use in another server.");
        verify(mockReplyAction).setEphemeral(true);
        verify(mockReplyAction).queue();
        verify(mockController, never()).join(any());
    }

    @Test
    void joinHappyPathCallsJoinAndReplies() {
        when(mockEvent.getName()).thenReturn("join");
        when(mockEvent.getMember()).thenReturn(mockMember);
        when(mockMember.getVoiceState()).thenReturn(mockVoiceState);
        when(mockVoiceState.inAudioChannel()).thenReturn(true);
        when(mockVoiceState.getChannel()).thenReturn(mockChannel);
        when(mockChannel.getName()).thenReturn("General");
        when(mockEvent.getGuild()).thenReturn(mockGuild);
        when(mockGuild.getIdLong()).thenReturn(111L);
        when(mockController.getConnectedGuildId()).thenReturn(null);

        listener.onSlashCommandInteraction(mockEvent);

        verify(mockController).join(mockChannel);
        verify(mockEvent).reply("Joined **General**.");
        verify(mockReplyAction).setEphemeral(true);
        verify(mockReplyAction).queue();
    }

    // --- /leave tests ---

    @Test
    void leaveWhenBotNotConnectedRepliesWithError() {
        when(mockEvent.getName()).thenReturn("leave");
        when(mockEvent.getGuild()).thenReturn(mockGuild);
        when(mockGuild.getIdLong()).thenReturn(111L);
        when(mockController.getConnectedGuildId()).thenReturn(null);

        listener.onSlashCommandInteraction(mockEvent);

        verify(mockEvent).reply("I'm not in a voice channel in this server.");
        verify(mockReplyAction).setEphemeral(true);
        verify(mockReplyAction).queue();
        verify(mockController, never()).leave();
    }

    @Test
    void leaveWhenBotInDifferentGuildRepliesWithError() {
        when(mockEvent.getName()).thenReturn("leave");
        when(mockEvent.getGuild()).thenReturn(mockGuild);
        when(mockGuild.getIdLong()).thenReturn(222L);
        when(mockController.getConnectedGuildId()).thenReturn(111L);

        listener.onSlashCommandInteraction(mockEvent);

        verify(mockEvent).reply("I'm not in a voice channel in this server.");
        verify(mockReplyAction).setEphemeral(true);
        verify(mockReplyAction).queue();
        verify(mockController, never()).leave();
    }

    @Test
    void leaveHappyPathCallsLeaveAndReplies() {
        when(mockEvent.getName()).thenReturn("leave");
        when(mockEvent.getGuild()).thenReturn(mockGuild);
        when(mockGuild.getIdLong()).thenReturn(111L);
        when(mockController.getConnectedGuildId()).thenReturn(111L);

        listener.onSlashCommandInteraction(mockEvent);

        verify(mockController).leave();
        verify(mockEvent).reply("Disconnected.");
        verify(mockReplyAction).setEphemeral(true);
        verify(mockReplyAction).queue();
    }

    @Test
    void unknownCommandIsIgnored() {
        when(mockEvent.getName()).thenReturn("unknown");

        listener.onSlashCommandInteraction(mockEvent);

        verifyNoInteractions(mockController);
        verify(mockEvent, never()).reply(anyString());
    }
}
