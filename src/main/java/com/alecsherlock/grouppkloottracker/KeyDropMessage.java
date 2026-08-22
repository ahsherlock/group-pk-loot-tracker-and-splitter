package com.alecsherlock.grouppkloottracker;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * A custom network message that is broadcasted to all party members
 * whenever a player in the session loots a PK key.
 */
@Getter
@AllArgsConstructor
public class KeyDropMessage extends PartyMemberMessage
{
    /**
     * The estimated GP value of the key parsed from the game chat.
     */
    private final int value;
}