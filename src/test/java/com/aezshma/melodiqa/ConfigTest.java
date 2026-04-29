package com.aezshma.melodiqa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.typesafe.config.ConfigException;
import org.junit.jupiter.api.Test;

public class ConfigTest {

    @Test
    void configParseTest() {
        final MelodiqaConfig config = MelodiqaConfig.fromResource("melodiqa.conf");
        assertEquals("Speakers", config.getAudioDeviceName());
        assertEquals("DISCORD_BOT_TOKEN", config.getDiscordBotToken());
    }

    @Test
    void configMissingTokenThrows() {
        assertThrows(ConfigException.class, () ->
            MelodiqaConfig.fromResource("melodiqa-missing-token.conf"));
    }

    @Test
    void configMissingDeviceThrows() {
        assertThrows(ConfigException.class, () ->
            MelodiqaConfig.fromResource("melodiqa-missing-device.conf"));
    }
}
