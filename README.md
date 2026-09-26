# Meteor Reporter

Meteor Reporter is a RuneLite plugin for sharing crashed Shooting Star sightings with other plugin users, and for scouting the stars that have not landed yet using player-owned house telescopes.

Everything shared through the plugin is also on the web at **[meteor.shep.rip](https://meteor.shep.rip)** — active stars on a map of Gielinor, upcoming stars from telescope readings, and a leaderboard of the people who share the most. The plugin links to it from the bottom of its panel. Clans can also have new stars posted straight to a Discord channel from the site's **Discord alerts** page, filtered by tier and by free-to-play or members worlds.

## Features

- Adds a **Report** option to crashed Shooting Stars, or reports them for you if you turn that on.
- Shares the star's world, tier, and landing spot.
- Shows active reports in the **Live** tab of the Meteor Reporter sidebar panel.
- Shows upcoming stars in the **Scouted** tab, from house telescope readings other players share.
- Optionally shows the first reporter's in-game name and contribution rank.
- Prevents the same active meteor from being claimed more than once.
- Updates a report as the star moves through its layers.
- Removes the listing when the final layer is completed.

## How to use

1. Install and enable **Meteor Reporter**.
2. Open the plugin settings and enable **Shared reports**.
3. Right-click a crashed Shooting Star and choose **Report** — or enable **Report stars automatically** and skip the right-click.
4. Open the Meteor Reporter sidebar panel to see active stars reported by other users.

Use **Minimum tier** to hide reports below a tier you care about; the panel says how many are hidden. Reports with no recent update are dimmed, because a star drops a size every seven minutes whether or not anybody is mining it.

Enable **Share telescope readings** to contribute to the **Scouted** tab. Looking through the telescope in your player-owned house tells you the region and time window of the next star on the world you are on, and that reading is shared so other people can plan ahead. Reading the tab does not require the setting; only contributing does.

Enable **Report stars automatically** and a star is shared the moment it comes into view, with no right-clicking. It is the same report the menu option sends, and you get the same chat line telling you it went. Stars someone has already shared are not sent again. This is off by default.

You can optionally enable **Show my IGN**. If you are the first person to report a meteor, your name and contribution count will appear on its listing, and you are ranked on the contributor leaderboard. Sharing anonymously counts for nothing on the leaderboard by design — no name is sent, and nothing ties your anonymous reports to each other. Name sharing is disabled by default.

The two settings combine, so it is worth being deliberate about it: with automatic reporting *and* your name on, passing a star publishes your name, the world and the spot on a public website without you doing anything. Leave **Show my IGN** off if you would rather that only happened when you choose to report.

Reports are refreshed automatically while the panel is open, and the **Refresh** button fetches them on demand. A completed star is removed when a nearby Meteor Reporter user witnesses its final layer disappear. Old reports also expire automatically.

## Privacy

Shared reports contain the star's world, tier, landing spot, coordinates, and update time. Shared telescope readings contain the world, the region named by the telescope, and the time window it gave. Neither contains your account name or information about nearby players.

Sharing is disabled by default because it communicates with a third-party service. As with any online service, the service operator may receive your IP address when you enable shared reports.

If **Show my IGN** is enabled, your RuneScape name is also included with reports, along with a random id that lets the leaderboard count them. With it disabled neither is sent, so anonymous reports cannot be linked to each other or to you. Contribution ranks are a fun community indicator and are not proof of account ownership.

**Report stars automatically** does not change what is sent, only when. A star you walk past is shared without being asked, so combined with **Show my IGN** it makes your whereabouts public each time. Both are off by default and either can be turned off on its own.

## License

BSD 2-Clause
