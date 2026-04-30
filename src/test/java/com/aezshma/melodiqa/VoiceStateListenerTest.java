package com.aezshma.melodiqa;

import static org.mockito.Mockito.*;

import java.util.List;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VoiceStateListenerTest {

    @Mock private BotController mockController;
    @Mock private GuildVoiceUpdateEvent mockEvent;
    @Mock private Guild mockGuild;
    @Mock private Member mockUser;
    @Mock private SelfMember mockBotMember;
    @Mock private User mockUserAccount;
    @Mock private SelfUser mockBotAccount;
    @Mock private AudioChannelUnion mockBotChannel;
    @Mock private AudioChannelUnion mockOtherChannel;

    private VoiceStateListener listener;

    @BeforeEach
    void setUp() {
        listener = new VoiceStateListener(mockController);
        when(mockEvent.getGuild()).thenReturn(mockGuild);
        when(mockGuild.getSelfMember()).thenReturn(mockBotMember);
        when(mockGuild.getIdLong()).thenReturn(750000000000000000L);
        when(mockController.getConnectedGuildId()).thenReturn(750000000000000000L);
        when(mockController.getConnectedChannel()).thenReturn(mockBotChannel);
        when(mockUser.getUser()).thenReturn(mockUserAccount);
        when(mockUserAccount.isBot()).thenReturn(false);
        when(mockBotMember.getUser()).thenReturn(mockBotAccount);
        when(mockBotAccount.isBot()).thenReturn(true);
    }

    @Test
    void botKickedCallsLeave() {
        // Same object reference so .equals() returns true
        when(mockEvent.getMember()).thenReturn(mockBotMember);
        when(mockEvent.getChannelLeft()).thenReturn(mockBotChannel);
        when(mockEvent.getChannelJoined()).thenReturn(null);

        listener.onGuildVoiceUpdate(mockEvent);

        verify(mockController).leave();
    }

    @Test
    void botMovedByAdminCallsLeave() {
        when(mockEvent.getMember()).thenReturn(mockBotMember);
        when(mockEvent.getChannelLeft()).thenReturn(mockBotChannel);
        when(mockEvent.getChannelJoined()).thenReturn(mockOtherChannel);

        listener.onGuildVoiceUpdate(mockEvent);

        verify(mockController).leave();
    }

    @Test
    void userLeavingBotChannelWithNoOtherUsersStartsIdleTimer() {
        when(mockEvent.getMember()).thenReturn(mockUser);
        when(mockEvent.getChannelLeft()).thenReturn(mockBotChannel);
        when(mockEvent.getChannelJoined()).thenReturn(null);
        // Only the bot remains after the user left
        when(mockBotChannel.getMembers()).thenReturn(List.of(mockBotMember));

        listener.onGuildVoiceUpdate(mockEvent);

        verify(mockController).startIdleTimer();
        verify(mockController, never()).cancelIdleTimer();
    }

    @Test
    void userLeavingBotChannelWithOtherUsersStillPresentDoesNotStartTimer() {
        final Member anotherUser = mock(Member.class);
        final User anotherUserAccount = mock(User.class);
        when(anotherUser.getUser()).thenReturn(anotherUserAccount);
        when(anotherUserAccount.isBot()).thenReturn(false);

        when(mockEvent.getMember()).thenReturn(mockUser);
        when(mockEvent.getChannelLeft()).thenReturn(mockBotChannel);
        when(mockEvent.getChannelJoined()).thenReturn(null);
        // Another human is still present
        when(mockBotChannel.getMembers()).thenReturn(List.of(mockBotMember, anotherUser));

        listener.onGuildVoiceUpdate(mockEvent);

        verify(mockController, never()).startIdleTimer();
        verify(mockController, never()).cancelIdleTimer();
    }

    @Test
    void userJoiningBotChannelCancelsIdleTimer() {
        when(mockEvent.getMember()).thenReturn(mockUser);
        when(mockEvent.getChannelJoined()).thenReturn(mockBotChannel);
        when(mockEvent.getChannelLeft()).thenReturn(null);

        listener.onGuildVoiceUpdate(mockEvent);

        verify(mockController).cancelIdleTimer();
        verify(mockController, never()).startIdleTimer();
    }

    @Test
    void userJoiningUnrelatedChannelIsIgnored() {
        when(mockEvent.getMember()).thenReturn(mockUser);
        when(mockEvent.getChannelJoined()).thenReturn(mockOtherChannel);
        when(mockEvent.getChannelLeft()).thenReturn(null);

        listener.onGuildVoiceUpdate(mockEvent);

        verify(mockController, never()).cancelIdleTimer();
        verify(mockController, never()).startIdleTimer();
    }

    @Test
    void eventFromUnrelatedGuildIsIgnored() {
        when(mockController.getConnectedGuildId()).thenReturn(850000000000000000L);
        when(mockEvent.getMember()).thenReturn(mockUser);
        when(mockEvent.getChannelLeft()).thenReturn(mockBotChannel);
        when(mockEvent.getChannelJoined()).thenReturn(null);

        listener.onGuildVoiceUpdate(mockEvent);

        verify(mockController, never()).startIdleTimer();
        verify(mockController, never()).cancelIdleTimer();
        verify(mockController, never()).leave();
    }

    @Test
    void eventWhenBotNotConnectedIsIgnored() {
        when(mockController.getConnectedChannel()).thenReturn(null);
        when(mockController.getConnectedGuildId()).thenReturn(null);
        when(mockEvent.getMember()).thenReturn(mockUser);
        when(mockEvent.getChannelLeft()).thenReturn(mockBotChannel);
        when(mockEvent.getChannelJoined()).thenReturn(null);

        listener.onGuildVoiceUpdate(mockEvent);

        verify(mockController, never()).startIdleTimer();
        verify(mockController, never()).cancelIdleTimer();
    }
}
