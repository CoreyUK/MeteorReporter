package com.meteorreporter;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A telescope reading: the region the next star will land in on a world, and the window it is due.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class StarScout
{
	private int world;
	private String region;
	private long earliestAt;
	private long latestAt;
	private long updatedAt;
	private String reporterId;
	private String reporterName;
	private int contributionCount;
	/**
	 * Sent so the server can time the window by its own clock instead of trusting ours. Absolute
	 * times are sent alongside them and are what the server hands back.
	 */
	private int earliestMinutes;
	private int latestMinutes;
}
