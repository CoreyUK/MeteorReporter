package com.meteorreporter;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Meteor Reporter",
	description = "Share crashed-star worlds, tiers, and landing spots, and scout stars yet to fall",
	tags = {"meteor", "shooting", "star", "mining", "report", "scout", "telescope"}
)
public class MeteorReporterPlugin extends Plugin
{
	private static final int REFRESH_SECONDS = 30;
	private static final int TIER_CHECK_TICKS = 5;
	private static final long SCOUT_REPOST_MILLIS = 60_000L;
	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private MeteorReporterConfig config;
	@Inject private MeteorReportClient reportClient;
	@Inject private ClientToolbar clientToolbar;
	@Inject private ScheduledExecutorService executor;
	@Inject private ConfigManager configManager;

	private final Map<WorldPoint, StarObservation> visibleStars = new HashMap<>();
	private final Set<WorldPoint> pendingCompletion = new HashSet<>();
	private final Set<WorldPoint> reportedHere = new HashSet<>();
	private final Set<WorldPoint> completedHere = new HashSet<>();
	private final Set<String> activeReportKeys = ConcurrentHashMap.newKeySet();
	private final AtomicBoolean refreshInFlight = new AtomicBoolean();
	private final AtomicBoolean scoutRefreshInFlight = new AtomicBoolean();
	private volatile boolean scoutsUnavailable;
	private String lastScoutKey;
	private long lastScoutAt;
	private volatile int currentWorld;
	private volatile boolean panelActive;
	private MeteorReporterPanel panel;
	private NavigationButton navigationButton;
	private ScheduledFuture<?> refreshTask;
	private int tierCheckTicks;

	@Override
	protected void startUp()
	{
		panel = new MeteorReporterPanel(this::requestRefresh, this::setPanelActive);
		navigationButton = NavigationButton.builder()
			.tooltip("Meteor Reporter")
			.icon(createIcon())
			.priority(6)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navigationButton);
		// Enabling the plugin mid-session doesn't fire a game state change, so seed the world here.
		clientThread.invoke(() -> currentWorld = client.getGameState() == GameState.LOGGED_IN ? client.getWorld() : 0);
		restartRefreshTask();
		log.debug("Meteor Reporter started");
	}

	@Override
	protected void shutDown()
	{
		stopRefreshTask();
		panelActive = false;
		visibleStars.clear();
		pendingCompletion.clear();
		reportedHere.clear();
		completedHere.clear();
		activeReportKeys.clear();
		refreshInFlight.set(false);
		scoutRefreshInFlight.set(false);
		scoutsUnavailable = false;
		lastScoutKey = null;
		currentWorld = 0;
		if (navigationButton != null) clientToolbar.removeNavigation(navigationButton);
		panel = null;
		navigationButton = null;
		log.debug("Meteor Reporter stopped");
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		GameObject object = event.getGameObject();
		int tier = StarTier.fromObjectId(object.getId());
		if (tier < 0) return;
		WorldPoint point = object.getWorldLocation();
		StarObservation observation = new StarObservation(point, tier);
		visibleStars.put(point, observation);
		pendingCompletion.remove(point);
		completedHere.remove(point);
		if (reportedHere.contains(point) && config.sharingEnabled()) sendReport(observation, false);
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		GameObject object = event.getGameObject();
		if (StarTier.fromObjectId(object.getId()) < 0) return;
		WorldPoint point = object.getWorldLocation();
		visibleStars.remove(point);
		pendingCompletion.add(point);
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		WorldView worldView = client.getTopLevelWorldView();
		for (WorldPoint point : new ArrayList<>(pendingCompletion))
		{
			pendingCompletion.remove(point);
			boolean witnessedCompletion = client.getGameState() == GameState.LOGGED_IN
				&& client.getLocalPlayer() != null
				&& client.getLocalPlayer().getWorldLocation().distanceTo(point) <= 32;
			if (visibleStars.containsKey(point) || !witnessedCompletion || !config.sharingEnabled()) continue;
			// A star that shrinks is replaced, and the despawn can arrive after the respawn. Take a
			// last look at the scene so a degrade is never mistaken for a completed star.
			int tier = worldView == null ? -1 : tierAt(worldView, point);
			if (tier > 0)
			{
				StarObservation survivor = new StarObservation(point, tier);
				visibleStars.put(point, survivor);
				if (reportedHere.contains(point)) sendReport(survivor, false);
				continue;
			}
			reportedHere.remove(point);
			completedHere.add(point);
			reportClient.delete(createReport(point, 1), () -> refreshReports(true),
				error -> log.debug("Unable to delete completed meteor report: {}", error));
		}
		if (++tierCheckTicks >= TIER_CHECK_TICKS)
		{
			tierCheckTicks = 0;
			verifyStarTiers();
		}
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!config.sharingEnabled() || event.getType() != MenuAction.EXAMINE_OBJECT.getId()) return;
		WorldView worldView = client.getWorldView(event.getMenuEntry().getWorldViewId());
		if (worldView == null) return;
		Tile tile = worldView.getScene().getTiles()[worldView.getPlane()][event.getActionParam0()][event.getActionParam1()];
		TileObject object = findObject(tile, event.getIdentifier());
		if (object == null || StarTier.fromObjectId(object.getId()) < 0) return;
		StarObservation observation = visibleStars.get(object.getWorldLocation());
		if (observation == null) return;
		if (reportedHere.contains(observation.getWorldPoint()) || activeReportKeys.contains(reportKey(client.getWorld(), observation.getWorldPoint()))) return;
		client.getMenu().createMenuEntry(-1)
			.setOption("Report")
			.setTarget(event.getTarget())
			.setType(MenuAction.RUNELITE)
			.onClick(entry -> sendReport(observation, true));
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			currentWorld = client.getWorld();
			return;
		}
		if (event.getGameState() == GameState.HOPPING || event.getGameState() == GameState.LOGIN_SCREEN)
		{
			currentWorld = 0;
			visibleStars.clear();
			pendingCompletion.clear();
			reportedHere.clear();
			completedHere.clear();
			activeReportKeys.clear();
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!config.sharingEnabled() || !config.shareTelescope()) return;
		ChatMessageType type = event.getType();
		if (type != ChatMessageType.GAMEMESSAGE && type != ChatMessageType.SPAM
			&& type != ChatMessageType.MESBOX && type != ChatMessageType.DIALOG)
		{
			return;
		}
		TelescopeHint hint = TelescopeHint.parse(event.getMessage());
		if (hint == null)
		{
			if (event.getMessage() != null && event.getMessage().toLowerCase().contains("telescope"))
			{
				log.debug("Unparsed telescope message: {}", event.getMessage());
			}
			return;
		}
		sendScout(hint);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!MeteorReporterConfig.GROUP.equals(event.getGroup())) return;
		// Give a server that has since gained the scouting endpoint another chance.
		scoutsUnavailable = false;
		restartRefreshTask();
	}

	/**
	 * A star drops a size every seven minutes rather than when its layer is mined out, and that
	 * change does not reliably arrive as a despawn and respawn, so re-read the tier from the scene.
	 */
	private void verifyStarTiers()
	{
		if (visibleStars.isEmpty()) return;
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null) return;
		for (StarObservation star : new ArrayList<>(visibleStars.values()))
		{
			WorldPoint point = star.getWorldPoint();
			int tier = tierAt(worldView, point);
			if (tier < 0 || tier == star.getTier()) continue;
			log.debug("Star at {} changed from tier {} to tier {}", point, star.getTier(), tier);
			StarObservation updated = new StarObservation(point, tier);
			visibleStars.put(point, updated);
			if (reportedHere.contains(point) && config.sharingEnabled()) sendReport(updated, false);
		}
	}

	private int tierAt(WorldView worldView, WorldPoint point)
	{
		LocalPoint local = LocalPoint.fromWorld(worldView, point);
		Tile[][][] tiles = worldView.getScene().getTiles();
		int plane = point.getPlane();
		if (local == null || plane < 0 || plane >= tiles.length) return -1;
		// A star reports one tile of its footprint, so sweep the tiles around it as well.
		for (int x = local.getSceneX() - 1; x <= local.getSceneX() + 1; x++)
		{
			for (int y = local.getSceneY() - 1; y <= local.getSceneY() + 1; y++)
			{
				if (x < 0 || y < 0 || x >= tiles[plane].length || y >= tiles[plane][x].length) continue;
				Tile tile = tiles[plane][x][y];
				if (tile == null) continue;
				for (GameObject object : tile.getGameObjects())
				{
					if (object == null || !point.equals(object.getWorldLocation())) continue;
					int tier = StarTier.fromObjectId(object.getId());
					if (tier > 0) return tier;
				}
			}
		}
		return -1;
	}

	private void sendReport(StarObservation star, boolean notify)
	{
		MeteorReport report = createReport(star.getWorldPoint(), star.getTier());
		log.debug("Submitting world {} tier {} at {}", report.getWorld(), report.getTier(), report.getSpot());
		reportClient.report(report, response -> clientThread.invoke(() ->
		{
			if (completedHere.contains(star.getWorldPoint()))
			{
				reportClient.delete(report, () -> refreshReports(true),
					error -> log.debug("Unable to remove a report completed during submission: {}", error));
				return;
			}
			log.debug("Report accepted for world {} tier {} (created={})", report.getWorld(), report.getTier(),
				response != null && response.isCreated());
			reportedHere.add(star.getWorldPoint());
			StarObservation current = visibleStars.get(star.getWorldPoint());
			if (current != null && current.getTier() != report.getTier())
			{
				sendReport(current, false);
			}
			if (notify)
			{
				String prefix = response != null && response.isCreated() ? "Reported" : "Already reported";
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
					prefix + " World " + report.getWorld() + " Tier " + report.getTier() + " at " + report.getSpot() + ".", null);
			}
			refreshReports(true);
		}), error ->
		{
			if (notify)
			{
				clientThread.invoke(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
					"Meteor report failed: " + error + ".", null));
			}
		});
	}

	private void sendScout(TelescopeHint hint)
	{
		long now = Instant.now().toEpochMilli();
		String key = client.getWorld() + ":" + hint.getRegion() + ":" + hint.getEarliestMinutes();
		// Looking through the telescope repeatedly should not repost the same reading.
		if (key.equals(lastScoutKey) && now - lastScoutAt < SCOUT_REPOST_MILLIS) return;
		lastScoutKey = key;
		lastScoutAt = now;
		String name = null;
		String contributorId = null;
		if (config.showReporterName() && client.getLocalPlayer() != null)
		{
			name = client.getLocalPlayer().getName();
			contributorId = reporterId();
		}
		StarScout scout = new StarScout(client.getWorld(), hint.getRegion(),
			now + hint.getEarliestMinutes() * 60_000L, now + hint.getLatestMinutes() * 60_000L,
			now, contributorId, name, 0, hint.getEarliestMinutes(), hint.getLatestMinutes());
		log.debug("Submitting scout for world {} region {} in {}-{} minutes", scout.getWorld(), scout.getRegion(),
			hint.getEarliestMinutes(), hint.getLatestMinutes());
		reportClient.reportScout(scout, this::refreshScouts,
			error -> log.debug("Unable to share telescope reading: {}", error));
	}

	private MeteorReport createReport(WorldPoint point, int tier)
	{
		String name = null;
		String contributorId = null;
		if (config.showReporterName() && client.getLocalPlayer() != null)
		{
			name = client.getLocalPlayer().getName();
			contributorId = reporterId();
		}
		return new MeteorReport(client.getWorld(), tier, point.getX(), point.getY(), point.getPlane(),
			StarSpot.forPoint(point), Instant.now().toEpochMilli(), contributorId, name, 0);
	}

	private void setPanelActive(boolean active)
	{
		panelActive = active;
		if (active) restartRefreshTask();
		else stopRefreshTask();
	}

	private void requestRefresh()
	{
		executor.execute(this::refreshAll);
	}

	private synchronized void restartRefreshTask()
	{
		stopRefreshTask();
		if (!config.sharingEnabled())
		{
			if (panel != null) SwingUtilities.invokeLater(panel::setDisabled);
			return;
		}
		// Only poll while the sidebar is open - nobody is looking at the list otherwise.
		if (!panelActive) return;
		refreshTask = executor.scheduleWithFixedDelay(this::refreshAll, 0,
			REFRESH_SECONDS, TimeUnit.SECONDS);
	}

	private synchronized void stopRefreshTask()
	{
		if (refreshTask != null)
		{
			refreshTask.cancel(false);
			refreshTask = null;
		}
	}

	private void refreshReports()
	{
		refreshReports(false);
	}

	/**
	 * @param fresh true after this client changed something. Such a refresh is never dropped for a
	 *              refresh already running, and asks any cache in front of the server to stand
	 *              aside, so a report shows up in the panel the moment it is made.
	 */
	private void refreshReports(boolean fresh)
	{
		if (!config.sharingEnabled() || panel == null) return;
		// Never stack requests - a slow or unreachable server would otherwise queue one every refresh.
		if (!refreshInFlight.compareAndSet(false, true) && !fresh) return;
		int world = currentWorld;
		int minimumTier = config.minimumTier();
		SwingUtilities.invokeLater(panel::setLoading);
		reportClient.list(fresh,
			reports ->
			{
				refreshInFlight.set(false);
				activeReportKeys.clear();
				List<MeteorReport> visible = new ArrayList<>();
				for (MeteorReport report : reports)
				{
					activeReportKeys.add(reportKey(report.getWorld(), report.getX(), report.getY(), report.getPlane()));
					if (report.getTier() >= minimumTier) visible.add(report);
				}
				int hidden = reports.size() - visible.size();
				SwingUtilities.invokeLater(() -> { if (panel != null) panel.setReports(visible, world, hidden); });
			},
			error ->
			{
				refreshInFlight.set(false);
				SwingUtilities.invokeLater(() -> { if (panel != null) panel.setError(error); });
			});
	}

	private void refreshAll()
	{
		refreshReports();
		refreshScouts();
	}

	private void refreshScouts()
	{
		if (!config.sharingEnabled() || panel == null || scoutsUnavailable) return;
		if (!scoutRefreshInFlight.compareAndSet(false, true)) return;
		int world = currentWorld;
		reportClient.listScouts(
			scouts ->
			{
				scoutRefreshInFlight.set(false);
				SwingUtilities.invokeLater(() -> { if (panel != null) panel.setScouts(scouts, world); });
			},
			(error, code) ->
			{
				scoutRefreshInFlight.set(false);
				// A server without the scouting endpoint should not be asked again every cycle.
				boolean missing = code == 404 || code == 501;
				if (missing) scoutsUnavailable = true;
				String message = missing ? "Scouted stars are not available yet" : error;
				SwingUtilities.invokeLater(() -> { if (panel != null) panel.setScoutError(message); });
			});
	}

	private String reporterId()
	{
		String existing = configManager.getConfiguration(MeteorReporterConfig.GROUP, "reporterId");
		if (existing != null)
		{
			try
			{
				UUID.fromString(existing);
				return existing;
			}
			catch (IllegalArgumentException ignored)
			{
				// Replace malformed local identifiers.
			}
		}
		String created = UUID.randomUUID().toString();
		configManager.setConfiguration(MeteorReporterConfig.GROUP, "reporterId", created);
		return created;
	}

	private static String reportKey(int world, WorldPoint point)
	{
		return reportKey(world, point.getX(), point.getY(), point.getPlane());
	}

	private static String reportKey(int world, int x, int y, int plane)
	{
		return world + ":" + x + ":" + y + ":" + plane;
	}

	private static TileObject findObject(Tile tile, int id)
	{
		if (tile == null) return null;
		for (GameObject object : tile.getGameObjects())
		{
			if (object != null && object.getId() == id) return object;
		}
		return null;
	}

	private static BufferedImage createIcon()
	{
		return ImageUtil.loadImageResource(MeteorReporterPlugin.class, "meteor.png");
	}

	@Provides
	MeteorReporterConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MeteorReporterConfig.class);
	}
}
