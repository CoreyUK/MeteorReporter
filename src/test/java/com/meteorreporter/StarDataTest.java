package com.meteorreporter;

import net.runelite.api.ObjectID;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

@SuppressWarnings("deprecation")
public class StarDataTest
{
	@Test
	public void mapsEveryStarTier()
	{
		assertEquals(1, StarTier.fromObjectId(ObjectID.CRASHED_STAR_41229));
		assertEquals(5, StarTier.fromObjectId(ObjectID.CRASHED_STAR_41225));
		assertEquals(9, StarTier.fromObjectId(ObjectID.CRASHED_STAR));
		assertEquals(-1, StarTier.fromObjectId(ObjectID.BANK_BOOTH));
	}

	@Test
	public void namesKnownAndUnknownSpots()
	{
		assertEquals("Rimmington mine", StarSpot.forPoint(new WorldPoint(2974, 3241, 0)));
		assertEquals("Unknown spot (1, 2)", StarSpot.forPoint(new WorldPoint(1, 2, 0)));
	}
}
