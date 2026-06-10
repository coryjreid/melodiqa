package com.aezshma.melodiqa;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.managers.AudioManager;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BotControllerTest {

    @Mock
    private Mixer mMockMixer;
    @Mock
    private ScheduledExecutorService mMockScheduler;
    @Mock
    private AudioChannel mMockChannel;
    @Mock
    private Guild mMockGuild;
    @Mock
    private AudioManager mMockAudioManager;
    @Mock
    private TargetDataLine mMockDataLine;
    @Mock
    private ScheduledFuture<?> mMockFuture;

    private BotController mController;

    @BeforeEach
    void setUp() throws Exception {
        when(mMockChannel.getGuild()).thenReturn(mMockGuild);
        when(mMockGuild.getIdLong()).thenReturn(12345L);
        when(mMockGuild.getAudioManager()).thenReturn(mMockAudioManager);
        when(mMockMixer.getLine(any(DataLine.Info.class))).thenReturn(mMockDataLine);
        mController = new BotController(mMockMixer, mMockScheduler);
    }

    @Test
    void initiallyNotConnected() {
        assertFalse(mController.isConnected());
        assertNull(mController.getConnectedGuildId());
        assertNull(mController.getConnectedChannel());
    }

    @Test
    void startIdleTimerSchedulesTask() {
        mController.startIdleTimer();
        verify(mMockScheduler).schedule(any(Runnable.class), eq(5L), eq(TimeUnit.MINUTES));
    }

    @Test
    void startIdleTimerCancelsExistingTimerFirst() {
        doReturn(mMockFuture).when(mMockScheduler)
            .schedule(any(Runnable.class), eq(5L), eq(TimeUnit.MINUTES));
        mController.startIdleTimer();
        mController.startIdleTimer();
        verify(mMockFuture).cancel(false);
        verify(mMockScheduler, times(2)).schedule(any(Runnable.class), eq(5L), eq(TimeUnit.MINUTES));
    }

    @Test
    void cancelIdleTimerCancelsFuture() {
        doReturn(mMockFuture).when(mMockScheduler)
            .schedule(any(Runnable.class), eq(5L), eq(TimeUnit.MINUTES));
        mController.startIdleTimer();
        mController.cancelIdleTimer();
        verify(mMockFuture).cancel(false);
    }

    @Test
    void cancelIdleTimerWithNoTimerDoesNothing() {
        assertDoesNotThrow(() -> mController.cancelIdleTimer());
    }

    @Test
    void leaveWhenNotConnectedDoesNothing() {
        assertDoesNotThrow(() -> mController.leave());
        verifyNoInteractions(mMockAudioManager);
    }

    @Test
    void joinSetsConnectedState() {
        mController.join(mMockChannel);

        assertTrue(mController.isConnected());
        assertEquals(12345L, mController.getConnectedGuildId());
        assertEquals(mMockChannel, mController.getConnectedChannel());
        verify(mMockAudioManager).openAudioConnection(mMockChannel);
        verify(mMockAudioManager).setSendingHandler(any());

        mController.leave();
    }

    @Test
    void leaveResetsConnectedState() {
        mController.join(mMockChannel);
        mController.leave();

        assertFalse(mController.isConnected());
        assertNull(mController.getConnectedGuildId());
        assertNull(mController.getConnectedChannel());
        verify(mMockAudioManager).closeAudioConnection();
    }

    @Test
    void leaveIsIdempotent() {
        mController.join(mMockChannel);
        mController.leave();
        assertDoesNotThrow(() -> mController.leave());
        verify(mMockAudioManager, times(1)).closeAudioConnection();
    }

    @Test
    void lineUnavailableExceptionDisconnects() throws Exception {
        when(mMockMixer.getLine(any(DataLine.Info.class)))
            .thenThrow(new LineUnavailableException("device busy"));

        mController.join(mMockChannel);
        Thread.sleep(200);

        assertFalse(mController.isConnected());
        assertNull(mController.getConnectedGuildId());
    }
}
