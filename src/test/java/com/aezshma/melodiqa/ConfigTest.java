package com.aezshma.melodiqa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class ConfigTest {

    @Test
    void configParseTest() {
        final MelodiqaConfig config = MelodiqaConfig.fromResource("melodiqa.conf");

        assertEquals("Speakers", config.getAudioDeviceName());
        assertEquals("DISCORD_BOT_TOKEN", config.getDiscordBotToken());
        assertEquals(1111111111111111111L, config.getGuildId());
        assertEquals(2222222222222222222L, config.getVoiceChannelId());
    }
}
