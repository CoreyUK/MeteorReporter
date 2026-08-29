package com.meteorreporter;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.runelite.api.GameObject;
import net.runelite.api.coords.WorldPoint;

@Getter
@AllArgsConstructor
class StarObservation
{
	private final GameObject object;
	private final WorldPoint worldPoint;
	private final int tier;
}
