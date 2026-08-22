package com.alecsherlock.grouppkloottracker;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Duration;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.PluginErrorPanel;
import net.runelite.client.ui.components.IconTextField;

/**
 * The sidebar UI panel for the PK Key Tracker.
 * Displays session controls, party management, and active party members
 * with real-time active and away time updating.
 */
public class GroupPkLootTrackerPanel extends PluginPanel
{
    private final GroupPkLootTrackerPlugin plugin;
    private final PartyService partyService;
    private final WSClient wsClient;
    private final GroupPkLootTrackerConfig config;

    private final JButton toggleSessionButton = new JButton();
    private final JPanel memberContainer = new JPanel();
    private final JPanel partyControlsPanel = new JPanel();
    private final Timer uiRefreshTimer;

    public GroupPkLootTrackerPanel(GroupPkLootTrackerPlugin plugin, PartyService partyService, WSClient wsClient, GroupPkLootTrackerConfig config)
    {
        this.plugin = plugin;
        this.partyService = partyService;
        this.wsClient = wsClient;
        this.config = config;

        setBorder(new EmptyBorder(10, 10, 10, 10));
        setLayout(new BorderLayout(0, 10));

        // Configure the top button
        toggleSessionButton.setFocusable(false);
        toggleSessionButton.setFont(FontManager.getRunescapeBoldFont());
        toggleSessionButton.addActionListener(e -> {
            if (plugin.isSessionActive()) {
                plugin.endSession();
            } else {
                plugin.startSession();
            }
        });

        // Configure the party controls (Join/Create)
        partyControlsPanel.setLayout(new BoxLayout(partyControlsPanel, BoxLayout.Y_AXIS));
        partyControlsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        buildPartyControls();

        // Configure the container that holds the list of players
        memberContainer.setLayout(new BoxLayout(memberContainer, BoxLayout.Y_AXIS));
        memberContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Wrap the member container in a scroll pane
        JScrollPane scrollPane = new JScrollPane(memberContainer);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Container to hold both the top button and the party controls
        JPanel topContainer = new JPanel();
        topContainer.setLayout(new BoxLayout(topContainer, BoxLayout.Y_AXIS));
        topContainer.add(partyControlsPanel);
        topContainer.add(Box.createRigidArea(new Dimension(0, 10)));
        topContainer.add(toggleSessionButton);

        add(topContainer, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        // Initialize a 1-second Swing Timer to continuously refresh active/away time durations
        this.uiRefreshTimer = new Timer(1000, e -> {
            if (plugin.isSessionActive())
            {
                updatePanel();
            }
        });
        this.uiRefreshTimer.start();

        // Run an initial update
        updatePanel();
    }

    /**
     * Builds the UI components for creating or joining a party.
     */
    private void buildPartyControls()
    {
        partyControlsPanel.removeAll();

        if (partyService.isInParty())
        {
            // Leave Party Button
            JButton leavePartyButton = new JButton("Leave Party");
            leavePartyButton.setFocusable(false);
            leavePartyButton.setAlignmentX(Component.CENTER_ALIGNMENT);
            leavePartyButton.setBackground(ColorScheme.PROGRESS_ERROR_COLOR.darker());
            leavePartyButton.addActionListener(e -> {
                if (plugin.isSessionActive()) {
                    plugin.endSession();
                }
                partyService.changeParty(null);
                updatePanel();
            });

            JLabel partyIdLabel = new JLabel("Party ID: " + partyService.getPartyId());
            partyIdLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            partyIdLabel.setFont(FontManager.getRunescapeSmallFont());

            partyControlsPanel.add(partyIdLabel);
            partyControlsPanel.add(Box.createRigidArea(new Dimension(0, 5)));
            partyControlsPanel.add(leavePartyButton);
        }
        else
        {
            // Join/Create Party Input
            IconTextField joinPartyInput = new IconTextField();
            joinPartyInput.setIcon(IconTextField.Icon.SEARCH);
            joinPartyInput.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 30));
            joinPartyInput.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            joinPartyInput.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
            joinPartyInput.setText("Enter passphrase...");

            // Clear text on click
            joinPartyInput.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (joinPartyInput.getText().equals("Enter passphrase...")) {
                        joinPartyInput.setText("");
                    }
                }
            });

            JButton joinButton = new JButton("Join / Create");
            joinButton.setFocusable(false);
            joinButton.setAlignmentX(Component.CENTER_ALIGNMENT);
            joinButton.addActionListener(e -> {
                String passphrase = joinPartyInput.getText().trim();
                if (!passphrase.isEmpty() && !passphrase.equals("Enter passphrase...")) {
                    partyService.changeParty(passphrase);
                    updatePanel();
                }
            });

            partyControlsPanel.add(joinPartyInput);
            partyControlsPanel.add(Box.createRigidArea(new Dimension(0, 5)));
            partyControlsPanel.add(joinButton);
        }

        partyControlsPanel.revalidate();
        partyControlsPanel.repaint();
    }

    /**
     * Refreshes the UI to match the current session state and party roster.
     */
    public void updatePanel()
    {
        memberContainer.removeAll();
        buildPartyControls();

        // Update Start/End button visibility and state
        toggleSessionButton.setVisible(partyService.isInParty());

        if (plugin.isSessionActive()) {
            toggleSessionButton.setText("End Session");
            toggleSessionButton.setBackground(ColorScheme.PROGRESS_ERROR_COLOR.darker());
        } else {
            toggleSessionButton.setText("Start Session");
            toggleSessionButton.setBackground(ColorScheme.PROGRESS_COMPLETE_COLOR.darker());
        }

        // Handle Empty States
        if (!partyService.isInParty()) {
            PluginErrorPanel errorPanel = new PluginErrorPanel();
            errorPanel.setContent("No Party", "Join or create a party above to start tracking splits.");
            memberContainer.add(errorPanel);
        } else if (!plugin.isSessionActive()) {
            PluginErrorPanel errorPanel = new PluginErrorPanel();
            errorPanel.setContent("Session Inactive", "Click 'Start Session' above when your PK trip begins.");
            memberContainer.add(errorPanel);
        } else if (partyService.getMembers().isEmpty()) {
            PluginErrorPanel errorPanel = new PluginErrorPanel();
            errorPanel.setContent("Waiting for members", "You are the only one in the party.");
            memberContainer.add(errorPanel);
        } else {
            // Render a card for each party member (including local player)
            memberContainer.add(buildMemberCard(partyService.getLocalMember()));
            memberContainer.add(Box.createRigidArea(new Dimension(0, 8)));

            for (PartyMember member : partyService.getMembers()) {
                if (member.getMemberId() != partyService.getLocalMember().getMemberId()) {
                    memberContainer.add(buildMemberCard(member));
                    memberContainer.add(Box.createRigidArea(new Dimension(0, 8)));
                }
            }
        }

        memberContainer.revalidate();
        memberContainer.repaint();
    }

    /**
     * Builds a UI card for a single party member showing their loot, inclusion status, and active/away time.
     */
    private JPanel buildMemberCard(PartyMember member)
    {
        if (member == null) return new JPanel();

        long id = member.getMemberId();
        String name = member.getDisplayName() != null ? member.getDisplayName() : "Unknown";

        // Highlight local player if name is unknown
        if (member == partyService.getLocalMember() && name.equals("Unknown")) {
            name = "You";
        }

        int looted = plugin.getMemberLoot().getOrDefault(id, 0);
        boolean included = plugin.getMemberIncluded().getOrDefault(id, false);
        MemberSessionTracker tracker = plugin.getSessionTrackers().get(id);

        JPanel card = new JPanel();
        card.setLayout(new BorderLayout());
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setBorder(new EmptyBorder(8, 8, 8, 8));

        // Checkbox and Time Info Container (Left)
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JCheckBox includeCheck = new JCheckBox(name, included);
        includeCheck.setForeground(Color.WHITE);
        includeCheck.setToolTipText("Include this player in the final Discord split calculation");
        includeCheck.addActionListener(e -> plugin.toggleMemberInclusion(id, includeCheck.isSelected()));
        leftPanel.add(includeCheck);

        // Add active / away time display if session is active
        if (tracker != null && plugin.isSessionActive())
        {
            Duration activeTime = tracker.getTotalActiveTime();
            String timeStr = String.format("Active: %dh %dm %ds", activeTime.toHours(), activeTime.toMinutesPart(), activeTime.toSecondsPart());

            Duration awayTime = tracker.getTotalAwayTime();
            if (awayTime.toSeconds() > 0)
            {
                timeStr += String.format(" (Away: %dh %dm)", awayTime.toHours(), awayTime.toMinutesPart());
            }

            JLabel timeLabel = new JLabel(timeStr);
            timeLabel.setFont(FontManager.getRunescapeSmallFont());

            if (tracker.isCurrentlyActive())
            {
                timeLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            }
            else
            {
                timeLabel.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
            }

            leftPanel.add(Box.createRigidArea(new Dimension(0, 2)));
            leftPanel.add(timeLabel);
        }

        // Looted Amount Label (Right)
        JLabel lootLabel = new JLabel(formatGp(looted));
        lootLabel.setForeground(ColorScheme.GRAND_EXCHANGE_PRICE);
        lootLabel.setFont(FontManager.getRunescapeSmallFont());
        lootLabel.setToolTipText("Total estimated value of keys looted by this player");

        card.add(leftPanel, BorderLayout.WEST);
        card.add(lootLabel, BorderLayout.EAST);

        return card;
    }

    /**
     * Formats an integer amount of GP into a readable string (e.g., 1.5m).
     */
    private String formatGp(long amount)
    {
        if (amount == 0) return "0 gp";
        if (amount >= 1_000_000_000) return String.format("%.2fb", amount / 1_000_000_000.0);
        if (amount >= 1_000_000) return String.format("%.2fm", amount / 1_000_000.0);
        if (amount >= 1_000) return String.format("%.1fk", amount / 1_000.0);
        return amount + " gp";
    }
}