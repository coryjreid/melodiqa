package com.aezshma.melodiqa;

import java.lang.invoke.MethodHandles;

import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SlashCommandListener extends ListenerAdapter {
    private static final Logger sLogger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final BotController mController;

    public SlashCommandListener(final BotController controller) {
        mController = controller;
    }

    @Override
    public void onSlashCommandInteraction(final SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "join" -> handleJoin(event);
            case "leave" -> handleLeave(event);
        }
    }

    private void handleJoin(final SlashCommandInteractionEvent event) {
        final Member member = event.getMember();
        final GuildVoiceState voiceState = member == null ? null : member.getVoiceState();

        if (voiceState == null || !voiceState.inAudioChannel()) {
            event.reply("You must be in a voice channel to use this command.")
                .setEphemeral(true).queue();
            return;
        }

        final long thisGuildId = event.getGuild().getIdLong();
        final Long connectedGuildId = mController.getConnectedGuildId();

        if (connectedGuildId != null && connectedGuildId.longValue() == thisGuildId) {
            event.reply("I'm already in a voice channel. Use `/leave` first.")
                .setEphemeral(true).queue();
            return;
        }

        if (connectedGuildId != null) {
            event.reply("I'm currently in use in another server.")
                .setEphemeral(true).queue();
            return;
        }

        final AudioChannel channel = voiceState.getChannel();
        mController.join(channel);
        event.reply("Joined **" + channel.getName() + "**.")
            .setEphemeral(true).queue();
    }

    private void handleLeave(final SlashCommandInteractionEvent event) {
        final long thisGuildId = event.getGuild().getIdLong();
        final Long connectedGuildId = mController.getConnectedGuildId();

        if (connectedGuildId == null || connectedGuildId.longValue() != thisGuildId) {
            event.reply("I'm not in a voice channel in this server.")
                .setEphemeral(true).queue();
            return;
        }

        mController.leave();
        event.reply("Disconnected.").setEphemeral(true).queue();
    }
}
