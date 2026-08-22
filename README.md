PK Key Tracker for RuneLite

The PK Key Tracker is a custom RuneLite plugin designed for Old School RuneScape (OSRS) players who participate in group Player vs. Player (PvP) combat in the wilderness. Its primary purpose is to eliminate the administrative headache of tracking loot drops, calculating split shares, and logging session histories across clanmates or party members.

Main Generalized Concept

At its core, the plugin bridges RuneLite's native Party Service with financial logistics. When a group of players forms a party and enters the wilderness, any "loot key" acquired by a member is automatically parsed from game chat and broadcasted across the party network via custom WebSockets.

The plugin maintains a shared ledger of total GP looted, tracks active participation and away durations, and—upon session conclusion—automatically computes exact balances (who owes the group versus who is owed) and transmits a rich, formatted ledger report directly to a designated Discord webhook.

Specific Features

RuneLite Party & WebSocket Integration: Leverages RuneLite's native infrastructure (PartyService and WSClient) to sync drop data instantly across multiple client instances without external server setup.

Automated Chat Parsing: Passively monitors chat messages for loot key notifications (e.g., "You receive a loot key! The loot is valued at around 1,500,000 coins"), extracting GP values automatically with zero manual entry required.

Real-Time Side Panel UI: A custom RuneLite sidebar panel featuring start/end session controls, party join/leave flows, and dynamic member cards displaying real-time loot values.

Active & Away Time Tracking: Automatically logs active intervals and calculates total active versus away durations for every party member throughout the session.

Automatic Inactivity Timeout: Background task checks every minute and automatically removes players from active split calculations if they remain inactive/away for 5 minutes or more.

Player Inclusion Toggles: Allows session leaders to manually include or exclude specific members (such as scouts or late arrivals) from the final split calculation.

Automated Discord Webhook Reporting: Compiles session duration, total loot, individual loot totals, active times, and exact debt/credit balances into a clean, color-coded Discord embed when the session ends.

How It Differs from Similar Plugin Hub Tools

While the RuneLite Plugin Hub contains several PvP and wealth-tracking tools, PK Key Tracker fills a unique administrative niche:

Unlike the Default RuneLite "Loot Tracker": The default tracker logs individual drops locally and privately. PK Key Tracker focuses entirely on group-shared wealth, multi-client synchronization, and automated party accounting.

Unlike Combat Performance Trackers (e.g., K/D and Killboards): Analytical plugins focus on combat mechanics, kill counts, and damage stats. PK Key Tracker ignores combat performance metrics entirely, focusing exclusively on the financial administration of the group trip.

Unlike Individual Discord Drop Loggers: Drop loggers spam Discord channels with real-time screenshots or notifications for every single drop. PK Key Tracker aggregates data quietly over hours of gameplay and generates a single, mathematically resolved debt ledger only at the end of the session.

Beyond the Native "Party" Plugin: RuneLite's base Party plugin shares combat stats (HP, Prayer, Vengeance). PK Key Tracker builds on top of that exact same network layer to handle party wealth distribution and session auditing.
