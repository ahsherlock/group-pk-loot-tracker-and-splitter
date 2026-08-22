package com.alecsherlock.grouppkloottracker;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Tracks the session history for a specific party member, including
 * total time active, time away, and individual active intervals.
 */
public class MemberSessionTracker
{
    @Getter
    private final long memberId;

    @Getter
    private final String displayName;

    // List of [StartTime, EndTime] intervals when the player was active
    private final List<ActiveInterval> activeIntervals = new ArrayList<>();

    // The time they most recently joined or rejoined
    private Instant currentJoinTime;

    // The time they most recently left or disconnected
    @Getter
    @Setter
    private Instant lastLeaveTime;

    @Getter
    @Setter
    private boolean currentlyActive;

    public MemberSessionTracker(long memberId, String displayName)
    {
        this.memberId = memberId;
        this.displayName = displayName;
        this.currentlyActive = false;
    }

    /**
     * Marks the player as joined/active.
     */
    public void markJoined()
    {
        if (!currentlyActive)
        {
            currentJoinTime = Instant.now();
            currentlyActive = true;
            lastLeaveTime = null; // Reset leave time since they are back
        }
    }

    /**
     * Marks the player as left/inactive and records the interval.
     */
    public void markLeft()
    {
        if (currentlyActive && currentJoinTime != null)
        {
            Instant now = Instant.now();
            activeIntervals.add(new ActiveInterval(currentJoinTime, now));
            lastLeaveTime = now;
            currentlyActive = false;
            currentJoinTime = null;
        }
    }

    /**
     * Calculates the total duration the player has been active in the session.
     */
    public Duration getTotalActiveTime()
    {
        Duration total = Duration.ZERO;
        for (ActiveInterval interval : activeIntervals)
        {
            total = total.plus(Duration.between(interval.start, interval.end));
        }

        // If they are currently active, add the time from their current join time
        if (currentlyActive && currentJoinTime != null)
        {
            total = total.plus(Duration.between(currentJoinTime, Instant.now()));
        }

        return total;
    }

    /**
     * Calculates the total duration the player has been away (inactive) since they first joined.
     * If they haven't joined yet, returns ZERO.
     */
    public Duration getTotalAwayTime()
    {
        if (activeIntervals.isEmpty() && !currentlyActive)
        {
            return Duration.ZERO; // Never joined
        }

        Instant firstJoin = null;
        if (!activeIntervals.isEmpty())
        {
            firstJoin = activeIntervals.get(0).start;
        }
        else if (currentJoinTime != null)
        {
            firstJoin = currentJoinTime;
        }

        if (firstJoin == null) return Duration.ZERO;

        Duration totalSessionDuration = Duration.between(firstJoin, Instant.now());
        return totalSessionDuration.minus(getTotalActiveTime());
    }

    private static class ActiveInterval
    {
        final Instant start;
        final Instant end;

        ActiveInterval(Instant start, Instant end)
        {
            this.start = start;
            this.end = end;
        }
    }
}