package com.aezshma.melodiqa;

import java.util.List;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VoiceStateListenerTest {

    @Mock
    private BotController mMockController;
    @Mock
    private GuildVoiceUpdateEvent mMockEvent;
    @Mock
    private Guild mMockGuild;
    @Mock
    private Member mMockUser;
    @Mock
    private SelfMember mMockBotMember;
    @Mock
    private User mMockUserAccount;
    @Mock
    private SelfUser mMockBotAccount;
    @Mock
    private AudioChannelUnion mMockBotChannel;
    @Mock
    private AudioChannelUnion mMockOtherChannel;

    private VoiceStateListener mListener;

    @BeforeEach
    void setUp() {
        mListener = new VoiceStateListener(mMockController);
        when(mMockEvent.getGuild()).thenReturn(mMockGuild);
        when(mMockGuild.getSelfMember()).thenReturn(mMockBotMember);
        when(mMockGuild.getIdLong()).thenReturn(750000000000000000L);
        when(mMockController.getConnectedGuildId()).thenReturn(750000000000000000L);
        when(mMockController.getConnectedChannel()).thenReturn(mMockBotChannel);
        when(mMockUser.getUser()).thenReturn(mMockUserAccount);
        when(mMockUserAccount.isBot()).thenReturn(false);
        when(mMockBotMember.getUser()).thenReturn(mMockBotAccount);
        when(mMockBotAccount.isBot()).thenReturn(true);
    }

    @Test
    void botKickedCallsLeave() {
        // Same object reference so .equals() returns true
        when(mMockEvent.getMember()).thenReturn(mMockBotMember);
        when(mMockEvent.getChannelLeft()).thenReturn(mMockBotChannel);
        when(mMockEvent.getChannelJoined()).thenReturn(null);

        mListener.onGuildVoiceUpdate(mMockEvent);

        verify(mMockController).leave();
    }

    @Test
    void botMovedByAdminCallsLeave() {
        when(mMockEvent.getMember()).thenReturn(mMockBotMember);
        when(mMockEvent.getChannelLeft()).thenReturn(mMockBotChannel);
        when(mMockEvent.getChannelJoined()).thenReturn(mMockOtherChannel);

        mListener.onGuildVoiceUpdate(mMockEvent);

        verify(mMockController).leave();
    }

    @Test
    void userLeavingBotChannelWithNoOtherUsersStartsIdleTimer() {
        when(mMockEvent.getMember()).thenReturn(mMockUser);
        when(mMockEvent.getChannelLeft()).thenReturn(mMockBotChannel);
        when(mMockEvent.getChannelJoined()).thenReturn(null);
        // Only the bot remains after the user left
        when(mMockBotChannel.getMembers()).thenReturn(List.of(mMockBotMember));

        mListener.onGuildVoiceUpdate(mMockEvent);

        verify(mMockController).startIdleTimer();
        verify(mMockController, never()).cancelIdleTimer();
    }

    @Test
    void userLeavingBotChannelWithOtherUsersStillPresentDoesNotStartTimer() {
        final Member anotherUser = mock(Member.class);
        final User anotherUserAccount = mock(User.class);
        when(anotherUser.getUser()).thenReturn(anotherUserAccount);
        when(anotherUserAccount.isBot()).thenReturn(false);

        when(mMockEvent.getMember()).thenReturn(mMockUser);
        when(mMockEvent.getChannelLeft()).thenReturn(mMockBotChannel);
        when(mMockEvent.getChannelJoined()).thenReturn(null);
        // Another human is still present
        when(mMockBotChannel.getMembers()).thenReturn(List.of(mMockBotMember, anotherUser));

        mListener.onGuildVoiceUpdate(mMockEvent);

        verify(mMockController, never()).startIdleTimer();
        verify(mMockController, never()).cancelIdleTimer();
    }

    @Test
    void userJoiningBotChannelCancelsIdleTimer() {
        when(mMockEvent.getMember()).thenReturn(mMockUser);
        when(mMockEvent.getChannelJoined()).thenReturn(mMockBotChannel);
        when(mMockEvent.getChannelLeft()).thenReturn(null);

        mListener.onGuildVoiceUpdate(mMockEvent);

        verify(mMockController).cancelIdleTimer();
        verify(mMockController, never()).startIdleTimer();
    }

    @Test
    void userJoiningUnrelatedChannelIsIgnored() {
        when(mMockEvent.getMember()).thenReturn(mMockUser);
        when(mMockEvent.getChannelJoined()).thenReturn(mMockOtherChannel);
        when(mMockEvent.getChannelLeft()).thenReturn(null);

        mListener.onGuildVoiceUpdate(mMockEvent);

        verify(mMockController, never()).cancelIdleTimer();
        verify(mMockController, never()).startIdleTimer();
    }

    @Test
    void eventFromUnrelatedGuildIsIgnored() {
        when(mMockController.getConnectedGuildId()).thenReturn(850000000000000000L);
        when(mMockEvent.getMember()).thenReturn(mMockUser);
        when(mMockEvent.getChannelLeft()).thenReturn(mMockBotChannel);
        when(mMockEvent.getChannelJoined()).thenReturn(null);

        mListener.onGuildVoiceUpdate(mMockEvent);

        verify(mMockController, never()).startIdleTimer();
        verify(mMockController, never()).cancelIdleTimer();
        verify(mMockController, never()).leave();
    }

    @Test
    void eventWhenBotNotConnectedIsIgnored() {
        when(mMockController.getConnectedChannel()).thenReturn(null);
        when(mMockController.getConnectedGuildId()).thenReturn(null);
        when(mMockEvent.getMember()).thenReturn(mMockUser);
        when(mMockEvent.getChannelLeft()).thenReturn(mMockBotChannel);
        when(mMockEvent.getChannelJoined()).thenReturn(null);

        mListener.onGuildVoiceUpdate(mMockEvent);

        verify(mMockController, never()).startIdleTimer();
        verify(mMockController, never()).cancelIdleTimer();
    }
}
