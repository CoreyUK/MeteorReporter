package com.meteorreporter;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MeteorReport
{
	private int world;
	private int tier;
	private int x;
	private int y;
	private int plane;
	private String spot;
	private long updatedAt;
	private String reporterId;
	private String reporterName;
	private int contributionCount;
}
