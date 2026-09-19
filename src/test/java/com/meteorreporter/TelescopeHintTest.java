package com.meteorreporter;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class TelescopeHintTest
{
	/** The real dialog, as seen through an oak telescope, with the line break the client sends. */
	private static final String GAME_MESSAGE = "You see a shooting star! The star looks like it will land in Piscatoris"
		+ "<br>or the Gnome Stronghold in the next 32 to 56 minutes.";

	@Test
	public void readsTheGamesOwnWording()
	{
		TelescopeHint hint = TelescopeHint.parse(GAME_MESSAGE);
		assertNotNull(hint);
		assertEquals("Piscatoris and the Gnome Stronghold", hint.getRegion());
		assertEquals(32, hint.getEarliestMinutes());
		assertEquals(56, hint.getLatestMinutes());
	}

	@Test
	public void keepsBothEndsOfAWindowThatRunsPastAnHour()
	{
		assertWindow(88, 112, "The star will land in Morytania in the next 1 hour 28 minutes to 1 hour 52 minutes.");
		assertWindow(88, 112, "The star will land in Morytania in the next 1 hour 28 to 1 hour 52 minutes.");
		assertWindow(88, 112, "The star will land in Morytania in the next 1 hour 28 to 52 minutes.");
		assertWindow(45, 69, "The star will land in Morytania in the next 45 minutes to 1 hour 9 minutes.");
		assertWindow(45, 69, "The star will land in Morytania in the next 45 to 1 hour 9 minutes.");
		assertWindow(120, 144, "The star will land in Morytania in the next 2 hours to 2 hours 24 minutes.");
		assertWindow(60, 120, "The star will land in Morytania in the next 1 to 2 hours.");
	}

	@Test
	public void readsADashedWindow()
	{
		assertWindow(5, 14, "Telescope: star over Asgarnia in 5-14 minutes.");
	}

	@Test
	public void ignoresNumbersAfterTheWindow()
	{
		assertWindow(32, 56, "Telescope: star over Asgarnia in the next 32 to 56 minutes. World 303, 2 players.");
	}

	private static void assertWindow(int earliest, int latest, String message)
	{
		TelescopeHint hint = TelescopeHint.parse(message);
		assertNotNull(message, hint);
		assertEquals(message, earliest, hint.getEarliestMinutes());
		assertEquals(message, latest, hint.getLatestMinutes());
	}

	@Test
	public void readsASingleMinuteWindow()
	{
		TelescopeHint hint = TelescopeHint.parse(
			"You look through the telescope. It looks like the next shooting star will land in "
				+ "approximately 14 minutes, somewhere in Asgarnia.");
		assertNotNull(hint);
		assertEquals("Asgarnia", hint.getRegion());
		assertEquals(14, hint.getEarliestMinutes());
		assertEquals(14, hint.getLatestMinutes());
	}

	@Test
	public void readsARangeInEitherOrder()
	{
		TelescopeHint hint = TelescopeHint.parse(
			"The telescope shows a star landing in the Kandarin region between 5 and 14 minutes from now.");
		assertNotNull(hint);
		assertEquals("Kandarin", hint.getRegion());
		assertEquals(5, hint.getEarliestMinutes());
		assertEquals(14, hint.getLatestMinutes());
	}

	@Test
	public void addsHoursToMinutesRatherThanTreatingThemAsARange()
	{
		TelescopeHint hint = TelescopeHint.parse(
			"You peer through the telescope and see a shooting star. It will land in about "
				+ "1 hour and 20 minutes in Morytania.");
		assertNotNull(hint);
		assertEquals("Morytania", hint.getRegion());
		assertEquals(80, hint.getEarliestMinutes());
		assertEquals(80, hint.getLatestMinutes());
	}

	@Test
	public void prefersTheLongerRegionName()
	{
		TelescopeHint hint = TelescopeHint.parse(
			"Telescope: a star will land in the Kharidian Desert in 9 minutes.");
		assertNotNull(hint);
		assertEquals("Kharidian Desert", hint.getRegion());
	}

	@Test
	public void stripsColourTags()
	{
		TelescopeHint hint = TelescopeHint.parse(
			"<col=ff0000>Your telescope shows a star over <col=00ff00>Varlamore</col> in 2 minutes.</col>");
		assertNotNull(hint);
		assertEquals("Varlamore", hint.getRegion());
		assertEquals(2, hint.getEarliestMinutes());
	}

	@Test
	public void ignoresAnEmptyTelescope()
	{
		assertNull(TelescopeHint.parse("You look through the telescope but you don't see anything interesting."));
	}

	@Test
	public void ignoresAMessageWithoutATime()
	{
		assertNull(TelescopeHint.parse("Your telescope points at Asgarnia."));
	}

	@Test
	public void ignoresUnrelatedChat()
	{
		assertNull(TelescopeHint.parse("I am selling 5 shark in Asgarnia for 20 minutes only"));
		assertNull(TelescopeHint.parse(null));
	}
}
