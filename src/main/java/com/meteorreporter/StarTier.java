package com.meteorreporter;

import net.runelite.api.ObjectID;

@SuppressWarnings("deprecation") // Crashed-star IDs are not currently exposed by gameval.ObjectID.
final class StarTier
{
	private static final int[] OBJECT_IDS =
	{
		ObjectID.CRASHED_STAR_41229,
		ObjectID.CRASHED_STAR_41228,
		ObjectID.CRASHED_STAR_41227,
		ObjectID.CRASHED_STAR_41226,
		ObjectID.CRASHED_STAR_41225,
		ObjectID.CRASHED_STAR_41224,
		ObjectID.CRASHED_STAR_41223,
		ObjectID.CRASHED_STAR_41021,
		ObjectID.CRASHED_STAR
	};

	private StarTier()
	{
	}

	static int fromObjectId(int objectId)
	{
		for (int i = 0; i < OBJECT_IDS.length; i++)
		{
			if (OBJECT_IDS[i] == objectId)
			{
				return i + 1;
			}
		}
		return -1;
	}
}
