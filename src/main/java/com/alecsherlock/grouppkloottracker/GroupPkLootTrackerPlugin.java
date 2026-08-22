package com.alecsherlock.grouppkloottracker;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
@PluginDescriptor(
        name = "Group PK Loot Tracker",
        description = "Tracks PK loot keys across a party and calculates splits for Discord.",
        tags = {"pk", "pvp", "loot", "key", "tracker", "party", "split"}
)
public class GroupPkLootTrackerPlugin extends Plugin
{
    private static final Pattern KEY_VALUE_PATTERN = Pattern.compile("valued at around ([0-9,]+) coins");

    @Inject private Client client;
    @Inject private PartyService partyService;
    @Inject private WSClient wsClient;
    @Inject private EventBus eventBus;
    @Inject private ClientToolbar clientToolbar;
    @Inject private GroupPkLootTrackerConfig config;
    @Inject private OkHttpClient okHttpClient;

    private GroupPkLootTrackerPanel panel;
    private NavigationButton navButton;

    @Getter
    private boolean sessionActive = false;
    private Instant sessionStartTime;

    @Getter
    private final Map<Long, Integer> memberLoot = new HashMap<>();

    @Getter
    private final Map<Long, Boolean> memberIncluded = new HashMap<>();

    @Getter
    private final Map<Long, MemberSessionTracker> sessionTrackers = new HashMap<>();

    @Provides
    GroupPkLootTrackerConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(GroupPkLootTrackerConfig.class);
    }

    @Override
    protected void startUp() throws Exception
    {
        wsClient.registerMessage(KeyDropMessage.class);

        panel = new GroupPkLootTrackerPanel(this, partyService, wsClient, config);

        BufferedImage icon;
        try
        {
            icon = ImageUtil.loadImageResource(getClass(), "icon.png");
        }
        catch (IllegalArgumentException e)
        {
            icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        }

        navButton = NavigationButton.builder()
                .tooltip("Group PK Loot Tracker")
                .icon(icon)
                .priority(5)
                .panel(panel)
                .build();

        clientToolbar.addNavigation(navButton);
    }

    @Override
    protected void shutDown() throws Exception
    {
        wsClient.unregisterMessage(KeyDropMessage.class);
        clientToolbar.removeNavigation(navButton);
        if (sessionActive)
        {
            endSession();
        }
    }

    public void startSession()
    {
        if (sessionActive) return;

        if (partyService.getMembers().isEmpty())
        {
            return;
        }

        sessionActive = true;
        sessionStartTime = Instant.now();
        memberLoot.clear();
        memberIncluded.clear();
        sessionTrackers.clear();

        for (PartyMember member : partyService.getMembers())
        {
            memberIncluded.put(member.getMemberId(), config.defaultInclude());

            MemberSessionTracker tracker = new MemberSessionTracker(member.getMemberId(), member.getDisplayName());
            tracker.markJoined();
            sessionTrackers.put(member.getMemberId(), tracker);
        }

        panel.updatePanel();
    }

    public void endSession()
    {
        if (!sessionActive) return;

        for (MemberSessionTracker tracker : sessionTrackers.values())
        {
            tracker.markLeft();
        }

        sendDiscordWebhook();

        sessionActive = false;
        memberLoot.clear();
        memberIncluded.clear();
        sessionTrackers.clear();
        panel.updatePanel();
    }

    public void toggleMemberInclusion(long memberId, boolean included)
    {
        memberIncluded.put(memberId, included);
        panel.updatePanel();
    }

    @Schedule(
            period = 1,
            unit = java.time.temporal.ChronoUnit.MINUTES,
            asynchronous = true
    )
    public void checkTimeouts()
    {
        if (!sessionActive) return;

        Instant now = Instant.now();
        boolean panelNeedsUpdate = false;

        for (Map.Entry<Long, MemberSessionTracker> entry : sessionTrackers.entrySet())
        {
            long memberId = entry.getKey();
            MemberSessionTracker tracker = entry.getValue();

            if (!tracker.isCurrentlyActive() && tracker.getLastLeaveTime() != null)
            {
                Duration timeAway = Duration.between(tracker.getLastLeaveTime(), now);

                if (timeAway.toMinutes() >= 5 && memberIncluded.getOrDefault(memberId, false))
                {
                    log.info("Removing player {} from session split due to 5 minute timeout.", tracker.getDisplayName());
                    memberIncluded.put(memberId, false);
                    panelNeedsUpdate = true;
                }
            }
        }

        if (panelNeedsUpdate)
        {
            SwingUtilities.invokeLater(() -> panel.updatePanel());
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (!sessionActive || event.getType() != ChatMessageType.GAMEMESSAGE)
        {
            return;
        }

        String message = Text.removeTags(event.getMessage());
        if (message.contains("You receive a loot key!"))
        {
            Matcher matcher = KEY_VALUE_PATTERN.matcher(message);
            if (matcher.find())
            {
                try
                {
                    int value = Integer.parseInt(matcher.group(1).replace(",", ""));
                    handleLocalKeyDrop(value);
                }
                catch (NumberFormatException e)
                {
                    log.error("Failed to parse loot key value: ", e);
                }
            }
        }
    }

    private void handleLocalKeyDrop(int value)
    {
        PartyMember localMember = partyService.getLocalMember();
        if (localMember != null)
        {
            addLoot(localMember.getMemberId(), value);
            partyService.send(new KeyDropMessage(value));
        }
    }

    @Subscribe
    public void onKeyDropMessage(KeyDropMessage event)
    {
        if (!sessionActive) return;
        addLoot(event.getMemberId(), event.getValue());
    }

    private void addLoot(long memberId, int value)
    {
        memberLoot.put(memberId, memberLoot.getOrDefault(memberId, 0) + value);
        panel.updatePanel();
    }

    private void sendDiscordWebhook()
    {
        String webhookUrl = config.discordWebhook();
        if (webhookUrl == null || webhookUrl.trim().isEmpty())
        {
            log.warn("PK Tracker: No Discord Webhook URL configured.");
            return;
        }

        int totalValue = 0;
        int includedCount = 0;

        for (PartyMember member : partyService.getMembers())
        {
            long id = member.getMemberId();
            if (memberIncluded.getOrDefault(id, false))
            {
                totalValue += memberLoot.getOrDefault(id, 0);
                includedCount++;
            }
        }

        if (includedCount == 0) return;
        int split = totalValue / includedCount;

        buildAndSendDiscordPayload(webhookUrl, totalValue, split);
    }

    private void buildAndSendDiscordPayload(String webhookUrl, int totalValue, int split)
    {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "⚔️ PK Session Concluded");
        embed.addProperty("color", 0xFF0000);

        Duration duration = Duration.between(sessionStartTime, Instant.now());
        String timeStr = String.format("%d hours, %d minutes", duration.toHours(), duration.toMinutesPart());

        StringBuilder description = new StringBuilder();
        description.append("**Duration:** ").append(timeStr).append("\n");
        description.append("**Total Loot:** ").append(formatGp(totalValue)).append("\n");
        description.append("**Split Amount:** ").append(formatGp(split)).append(" (per person)\n\n");
        description.append("**__Ledger Breakdown__**\n");

        for (PartyMember member : partyService.getMembers())
        {
            long id = member.getMemberId();
            if (memberIncluded.getOrDefault(id, false))
            {
                int looted = memberLoot.getOrDefault(id, 0);
                int balance = looted - split;

                String status = balance >= 0
                        ? "🔴 Owes Group: **" + formatGp(balance) + "**"
                        : "🟢 Owed: **" + formatGp(Math.abs(balance)) + "**";

                MemberSessionTracker tracker = sessionTrackers.get(id);
                String activeTimeStr = "Unknown";
                String awayTimeStr = "";

                if (tracker != null)
                {
                    Duration activeTime = tracker.getTotalActiveTime();
                    activeTimeStr = String.format("%dh %dm", activeTime.toHours(), activeTime.toMinutesPart());

                    Duration awayTime = tracker.getTotalAwayTime();
                    if (awayTime.toMinutes() > 0)
                    {
                        awayTimeStr = String.format(" (Away: %dh %dm)", awayTime.toHours(), awayTime.toMinutesPart());
                    }
                }

                description.append("`").append(member.getDisplayName()).append("` - Looted: ").append(formatGp(looted)).append("\n");
                description.append("> Active: ").append(activeTimeStr).append(awayTimeStr).append("\n");
                description.append("> ").append(status).append("\n\n");
            }
        }

        embed.addProperty("description", description.toString());

        JsonArray embeds = new JsonArray();
        embeds.add(embed);

        JsonObject payload = new JsonObject();
        payload.add("embeds", embeds);

        RequestBody body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), payload.toString());
        Request request = new Request.Builder().url(webhookUrl).post(body).build();

        okHttpClient.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException e)
            {
                log.error("Error sending Discord webhook", e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException
            {
                response.close();
            }
        });
    }

    private String formatGp(long amount)
    {
        if (amount >= 1_000_000_000) return String.format("%.2fb", amount / 1_000_000_000.0);
        if (amount >= 1_000_000) return String.format("%.2fm", amount / 1_000_000.0);
        if (amount >= 1_000) return String.format("%.1fk", amount / 1_000.0);
        return amount + " gp";
    }
}