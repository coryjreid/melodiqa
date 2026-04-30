package com.aezshma.melodiqa;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BotControllerTest {

    @Mock private Mixer mockMixer;
    @Mock private ScheduledExecutorService mockScheduler;
    @Mock private AudioChannel mockChannel;
    @Mock private Guild mockGuild;
    @Mock private AudioManager mockAudioManager;
    @Mock private TargetDataLine mockDataLine;
    @Mock private ScheduledFuture<?> mockFuture;

    private BotController controller;

    @BeforeEach
    void setUp() throws Exception {
        when(mockChannel.getGuild()).thenReturn(mockGuild);
        when(mockGuild.getIdLong()).thenReturn(12345L);
        when(mockGuild.getAudioManager()).thenReturn(mockAudioManager);
        when(mockMixer.getLine(any(DataLine.Info.class))).thenReturn(mockDataLine);
        controller = new BotController(mockMixer, mockScheduler);
    }

    @Test
    void initiallyNotConnected() {
        assertFalse(controller.isConnected());
        assertNull(controller.getConnectedGuildId());
        assertNull(controller.getConnectedChannel());
    }

    @Test
    void startIdleTimerSchedulesTask() {
        controller.startIdleTimer();
        verify(mockScheduler).schedule(any(Runnable.class), eq(5L), eq(TimeUnit.MINUTES));
    }

    @Test
    void startIdleTimerCancelsExistingTimerFirst() {
        doReturn(mockFuture).when(mockScheduler)
            .schedule(any(Runnable.class), eq(5L), eq(TimeUnit.MINUTES));
        controller.startIdleTimer();
        controller.startIdleTimer();
        verify(mockFuture).cancel(false);
        verify(mockScheduler, times(2)).schedule(any(Runnable.class), eq(5L), eq(TimeUnit.MINUTES));
    }

    @Test
    void cancelIdleTimerCancelsFuture() {
        doReturn(mockFuture).when(mockScheduler)
            .schedule(any(Runnable.class), eq(5L), eq(TimeUnit.MINUTES));
        controller.startIdleTimer();
        controller.cancelIdleTimer();
        verify(mockFuture).cancel(false);
    }

    @Test
    void cancelIdleTimerWithNoTimerDoesNothing() {
        assertDoesNotThrow(() -> controller.cancelIdleTimer());
    }

    @Test
    void leaveWhenNotConnectedDoesNothing() {
        assertDoesNotThrow(() -> controller.leave());
        verifyNoInteractions(mockAudioManager);
    }

    @Test
    void joinSetsConnectedState() {
        controller.join(mockChannel);

        assertTrue(controller.isConnected());
        assertEquals(12345L, controller.getConnectedGuildId());
        assertEquals(mockChannel, controller.getConnectedChannel());
        verify(mockAudioManager).openAudioConnection(mockChannel);
        verify(mockAudioManager).setSendingHandler(any());

        controller.leave();
    }

    @Test
    void leaveResetsConnectedState() {
        controller.join(mockChannel);
        controller.leave();

        assertFalse(controller.isConnected());
        assertNull(controller.getConnectedGuildId());
        assertNull(controller.getConnectedChannel());
        verify(mockAudioManager).closeAudioConnection();
    }

    @Test
    void leaveIsIdempotent() {
        controller.join(mockChannel);
        controller.leave();
        assertDoesNotThrow(() -> controller.leave());
        verify(mockAudioManager, times(1)).closeAudioConnection();
    }

    @Test
    void lineUnavailableExceptionDisconnects() throws Exception {
        when(mockMixer.getLine(any(DataLine.Info.class)))
            .thenThrow(new LineUnavailableException("device busy"));

        controller.join(mockChannel);
        Thread.sleep(200);

        assertFalse(controller.isConnected());
        assertNull(controller.getConnectedGuildId());
    }
}
