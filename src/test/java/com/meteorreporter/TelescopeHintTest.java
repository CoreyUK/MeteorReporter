package com.meteorreporter;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class TelescopeHintTest
{
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
