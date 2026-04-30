package com.aezshma.melodiqa;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SlashCommandListenerTest {

    @Mock
    private BotController mMockController;
    @Mock
    private SlashCommandInteractionEvent mMockEvent;
    @Mock
    private Member mMockMember;
    @Mock
    private GuildVoiceState mMockVoiceState;
    @Mock
    private AudioChannelUnion mMockChannel;
    @Mock
    private Guild mMockGuild;
    @Mock
    private ReplyCallbackAction mMockReplyAction;

    private SlashCommandListener mListener;

    @BeforeEach
    void setUp() {
        mListener = new SlashCommandListener(mMockController);
        lenient().when(mMockEvent.reply(anyString())).thenReturn(mMockReplyAction);
        lenient().when(mMockReplyAction.setEphemeral(anyBoolean())).thenReturn(mMockReplyAction);
    }

    // --- /join tests ---

    @Test
    void joinWhenUserNotInVoiceChannelRepliesWithError() {
        when(mMockEvent.getName()).thenReturn("join");
        when(mMockEvent.getMember()).thenReturn(mMockMember);
        when(mMockMember.getVoiceState()).thenReturn(mMockVoiceState);
        when(mMockVoiceState.inAudioChannel()).thenReturn(false);

        mListener.onSlashCommandInteraction(mMockEvent);

        verify(mMockEvent).reply("You must be in a voice channel to use this command.");
        verify(mMockReplyAction).setEphemeral(true);
        verify(mMockReplyAction).queue();
        verifyNoInteractions(mMockController);
    }

    @Test
    void joinWhenBotAlreadyInThisGuildRepliesWithError() {
        when(mMockEvent.getName()).thenReturn("join");
        when(mMockEvent.getMember()).thenReturn(mMockMember);
        when(mMockMember.getVoiceState()).thenReturn(mMockVoiceState);
        when(mMockVoiceState.inAudioChannel()).thenReturn(true);
        when(mMockEvent.getGuild()).thenReturn(mMockGuild);
        when(mMockGuild.getIdLong()).thenReturn(750000000000000000L);
        when(mMockController.getConnectedGuildId()).thenReturn(750000000000000000L);

        mListener.onSlashCommandInteraction(mMockEvent);

        verify(mMockEvent).reply("I'm already in a voice channel. Use `/leave` first.");
        verify(mMockReplyAction).setEphemeral(true);
        verify(mMockReplyAction).queue();
        verify(mMockController, never()).join(any());
    }

    @Test
    void joinWhenBotBusyInAnotherGuildRepliesWithError() {
        when(mMockEvent.getName()).thenReturn("join");
        when(mMockEvent.getMember()).thenReturn(mMockMember);
        when(mMockMember.getVoiceState()).thenReturn(mMockVoiceState);
        when(mMockVoiceState.inAudioChannel()).thenReturn(true);
        when(mMockEvent.getGuild()).thenReturn(mMockGuild);
        when(mMockGuild.getIdLong()).thenReturn(850000000000000000L);
        when(mMockController.getConnectedGuildId()).thenReturn(750000000000000000L);

        mListener.onSlashCommandInteraction(mMockEvent);

        verify(mMockEvent).reply("I'm currently in use in another server.");
        verify(mMockReplyAction).setEphemeral(true);
        verify(mMockReplyAction).queue();
        verify(mMockController, never()).join(any());
    }

    @Test
    void joinHappyPathCallsJoinAndReplies() {
        when(mMockEvent.getName()).thenReturn("join");
        when(mMockEvent.getMember()).thenReturn(mMockMember);
        when(mMockMember.getVoiceState()).thenReturn(mMockVoiceState);
        when(mMockVoiceState.inAudioChannel()).thenReturn(true);
        when(mMockVoiceState.getChannel()).thenReturn(mMockChannel);
        when(mMockChannel.getName()).thenReturn("General");
        when(mMockEvent.getGuild()).thenReturn(mMockGuild);
        when(mMockGuild.getIdLong()).thenReturn(750000000000000000L);
        when(mMockController.getConnectedGuildId()).thenReturn(null);

        mListener.onSlashCommandInteraction(mMockEvent);

        verify(mMockController).join(mMockChannel);
        verify(mMockEvent).reply("Joined **General**.");
        verify(mMockReplyAction).setEphemeral(true);
        verify(mMockReplyAction).queue();
    }

    @Test
    void joinWhenMemberIsNullRepliesWithError() {
        when(mMockEvent.getName()).thenReturn("join");
        when(mMockEvent.getMember()).thenReturn(null);

        mListener.onSlashCommandInteraction(mMockEvent);

        verify(mMockEvent).reply("You must be in a voice channel to use this command.");
        verify(mMockReplyAction).setEphemeral(true);
        verify(mMockReplyAction).queue();
        verifyNoInteractions(mMockController);
    }

    // --- /leave tests ---

    @Test
    void leaveWhenBotNotConnectedRepliesWithError() {
        when(mMockEvent.getName()).thenReturn("leave");
        when(mMockEvent.getGuild()).thenReturn(mMockGuild);
        when(mMockGuild.getIdLong()).thenReturn(750000000000000000L);
        when(mMockController.getConnectedGuildId()).thenReturn(null);

        mListener.onSlashCommandInteraction(mMockEvent);

        verify(mMockEvent).reply("I'm not in a voice channel in this server.");
        verify(mMockReplyAction).setEphemeral(true);
        verify(mMockReplyAction).queue();
        verify(mMockController, never()).leave();
    }

    @Test
    void leaveWhenBotInDifferentGuildRepliesWithError() {
        when(mMockEvent.getName()).thenReturn("leave");
        when(mMockEvent.getGuild()).thenReturn(mMockGuild);
        when(mMockGuild.getIdLong()).thenReturn(850000000000000000L);
        when(mMockController.getConnectedGuildId()).thenReturn(750000000000000000L);

        mListener.onSlashCommandInteraction(mMockEvent);

        verify(mMockEvent).reply("I'm not in a voice channel in this server.");
        verify(mMockReplyAction).setEphemeral(true);
        verify(mMockReplyAction).queue();
        verify(mMockController, never()).leave();
    }

    @Test
    void leaveHappyPathCallsLeaveAndReplies() {
        when(mMockEvent.getName()).thenReturn("leave");
        when(mMockEvent.getGuild()).thenReturn(mMockGuild);
        when(mMockGuild.getIdLong()).thenReturn(750000000000000000L);
        when(mMockController.getConnectedGuildId()).thenReturn(750000000000000000L);

        mListener.onSlashCommandInteraction(mMockEvent);

        verify(mMockController).leave();
        verify(mMockEvent).reply("Disconnected.");
        verify(mMockReplyAction).setEphemeral(true);
        verify(mMockReplyAction).queue();
    }

    @Test
    void unknownCommandIsIgnored() {
        when(mMockEvent.getName()).thenReturn("unknown");

        mListener.onSlashCommandInteraction(mMockEvent);

        verifyNoInteractions(mMockController);
        verify(mMockEvent, never()).reply(anyString());
    }
}
