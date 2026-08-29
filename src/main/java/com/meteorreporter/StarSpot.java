package com.meteorreporter;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;

final class StarSpot
{
	private static final Map<Long, String> SPOTS = new HashMap<>();

	static
	{
		spot(2974, 3241, "Rimmington mine");
		spot(2940, 3280, "Crafting Guild");
		spot(2906, 3355, "West Falador mine");
		spot(3030, 3348, "East Falador bank");
		spot(3018, 3443, "North Dwarven Mine entrance");
		spot(2882, 3474, "Taverley house portal");
		spot(2736, 3221, "Brimhaven northwest gold mine");
		spot(2742, 3143, "Brimhaven south dungeon entrance");
		spot(2845, 3037, "Nature Altar mine north of Shilo");
		spot(2827, 2999, "Shilo Village gem mine");
		spot(2835, 3296, "North Crandor");
		spot(2822, 3238, "South Crandor");
		spot(3296, 3298, "Al Kharid mine");
		spot(3276, 3164, "Al Kharid bank");
		spot(3341, 3267, "Emir's Arena");
		spot(3424, 3160, "Northwest of Uzer");
		spot(3434, 2889, "Nardah bank");
		spot(3316, 2867, "Agility Pyramid mine");
		spot(3171, 2910, "Desert Quarry mine");
		spot(2567, 2858, "Corsair Cove bank");
		spot(2483, 2886, "Corsair Resource Area");
		spot(2468, 2842, "Myths' Guild");
		spot(2571, 2964, "Feldip Hills fairy ring");
		spot(2630, 2993, "Rantz's cave");
		spot(2200, 2792, "Soul Wars south mine");
		spot(3818, 3801, "Fossil Island Volcanic Mine entrance");
		spot(3774, 3814, "Fossil Island rune rocks");
		spot(3686, 2969, "Mos Le'Harmless west bank");
		spot(2727, 3683, "Keldagrim entrance mine");
		spot(2683, 3699, "Rellekka mine");
		spot(2393, 3814, "Jatizso mine entrance");
		spot(2375, 3832, "Neitiznot rune rock");
		spot(2528, 3887, "Miscellania mine");
		spot(2139, 3938, "Lunar Isle mine entrance");
		spot(2602, 3086, "Yanille bank");
		spot(2624, 3141, "Port Khazard mine");
		spot(2608, 3233, "Ardougne Monastery");
		spot(2705, 3333, "South of Legends' Guild");
		spot(2804, 3434, "Catherby bank");
		spot(2589, 3478, "Coal Trucks west of Seers' Village");
		spot(1778, 3493, "Hosidius mine");
		spot(1769, 3709, "Port Piscarilius mine");
		spot(1597, 3648, "Shayzien mine");
		spot(1534, 3747, "South Lovakengj bank");
		spot(1437, 3840, "Lovakite mine");
		spot(1760, 3853, "Arceuus dense essence mine");
		spot(1322, 3816, "Mount Karuulm bank");
		spot(1279, 3817, "Mount Karuulm mine");
		spot(1210, 3651, "Kebos Swamp mine");
		spot(1258, 3564, "Chambers of Xeric bank");
		spot(3258, 3408, "Varrock east bank");
		spot(3290, 3353, "Southeast Varrock mine");
		spot(3175, 3362, "Champions' Guild mine");
		spot(3094, 3235, "Draynor Village");
		spot(3153, 3150, "West Lumbridge Swamp mine");
		spot(3230, 3155, "East Lumbridge Swamp mine");
		spot(3635, 3340, "Darkmeyer essence mine entrance");
		spot(3650, 3214, "Theatre of Blood bank");
		spot(3505, 3485, "Canifis bank");
		spot(3500, 3219, "Burgh de Rott bank");
		spot(3451, 3233, "Abandoned Mine west of Burgh de Rott");
		spot(2444, 3490, "West of Grand Tree");
		spot(2448, 3436, "Gnome Stronghold spirit tree");
		spot(2341, 3635, "Piscatoris fairy ring");
		spot(2329, 3163, "Lletya");
		spot(2269, 3158, "Isafdar runite rocks");
		spot(3274, 6055, "Prifddinas Zalcano entrance");
		spot(2318, 3269, "Arandar mine");
		spot(2173, 3409, "Mynydd northwest of Prifddinas");
		spot(3108, 3569, "Mage of Zamorak mine (level 7 Wilderness)");
		spot(3018, 3593, "Skeleton mine (level 10 Wilderness)");
		spot(3093, 3756, "Hobgoblin mine (level 30 Wilderness)");
		spot(3057, 3887, "Lava Maze runite mine (level 46 Wilderness)");
		spot(3049, 3940, "Pirates' Hideout (level 53 Wilderness)");
		spot(3091, 3962, "Mage Arena bank (level 56 Wilderness)");
		spot(3188, 3932, "Wilderness Resource Area");
	}

	private StarSpot()
	{
	}

	private static void spot(int x, int y, String name)
	{
		SPOTS.put(key(x, y), name);
	}

	static String forPoint(WorldPoint point)
	{
		return SPOTS.getOrDefault(key(point.getX(), point.getY()),
			"Unknown spot (" + point.getX() + ", " + point.getY() + ")");
	}

	private static long key(int x, int y)
	{
		return ((long) x << 32) | (y & 0xffffffffL);
	}
}
