package com.meteorreporter;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
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
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.WorldService;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.WorldUtil;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.http.api.worlds.WorldType;

@Slf4j
@PluginDescriptor(
	name = "Meteor Reporter",
	description = "Share crashed-star worlds, tiers, and landing spots",
	tags = {"meteor", "shooting", "star", "mining", "report"}
)
public class MeteorReporterPlugin extends Plugin
{
	private static final int REFRESH_SECONDS = 30;
	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private MeteorReporterConfig config;
	@Inject private MeteorReportClient reportClient;
	@Inject private ClientToolbar clientToolbar;
	@Inject private ScheduledExecutorService executor;
	@Inject private ConfigManager configManager;
	@Inject private WorldService worldService;

	private final Map<WorldPoint, StarObservation> visibleStars = new HashMap<>();
	private final Set<WorldPoint> pendingCompletion = new HashSet<>();
	private final Set<WorldPoint> reportedHere = new HashSet<>();
	private final Set<WorldPoint> completedHere = new HashSet<>();
	private final Set<String> activeReportKeys = ConcurrentHashMap.newKeySet();
	private MeteorReporterPanel panel;
	private NavigationButton navigationButton;
	private ScheduledFuture<?> refreshTask;
	private net.runelite.api.World hopTarget;
	private int hopAttempts;

	@Override
	protected void startUp()
	{
		panel = new MeteorReporterPanel(this::hopToWorld);
		navigationButton = NavigationButton.builder()
			.tooltip("Meteor Reporter")
			.icon(createIcon())
			.priority(6)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navigationButton);
		restartRefreshTask();
		log.debug("Meteor Reporter started");
	}

	@Override
	protected void shutDown()
	{
		if (refreshTask != null)
		{
			refreshTask.cancel(true);
			refreshTask = null;
		}
		visibleStars.clear();
		pendingCompletion.clear();
		reportedHere.clear();
		completedHere.clear();
		activeReportKeys.clear();
		hopTarget = null;
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
		for (WorldPoint point : new ArrayList<>(pendingCompletion))
		{
			pendingCompletion.remove(point);
			boolean witnessedCompletion = client.getGameState() == GameState.LOGGED_IN
				&& client.getLocalPlayer() != null
				&& client.getLocalPlayer().getWorldLocation().distanceTo(point) <= 32;
			if (!visibleStars.containsKey(point) && witnessedCompletion && config.sharingEnabled())
			{
				reportedHere.remove(point);
				completedHere.add(point);
				reportClient.delete(createReport(point, 1), this::refreshReports,
					error -> log.debug("Unable to delete completed meteor report: {}", error));
			}
		}
		if (hopTarget != null)
		{
			if (client.getWidget(InterfaceID.Worldswitcher.BUTTONS) == null)
			{
				client.openWorldHopper();
				if (++hopAttempts >= 3) hopTarget = null;
			}
			else
			{
				client.hopToWorld(hopTarget);
				hopTarget = null;
				hopAttempts = 0;
			}
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
		if (event.getGameState() == GameState.HOPPING || event.getGameState() == GameState.LOGIN_SCREEN)
		{
			visibleStars.clear();
			pendingCompletion.clear();
			reportedHere.clear();
			completedHere.clear();
			activeReportKeys.clear();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (MeteorReporterConfig.GROUP.equals(event.getGroup())) restartRefreshTask();
	}

	private void sendReport(StarObservation star, boolean notify)
	{
		MeteorReport report = createReport(star.getWorldPoint(), star.getTier());
		reportClient.report(report, response -> clientThread.invoke(() ->
		{
			if (completedHere.contains(star.getWorldPoint()))
			{
				reportClient.delete(report, this::refreshReports,
					error -> log.debug("Unable to remove a report completed during submission: {}", error));
				return;
			}
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
			refreshReports();
		}), error ->
		{
			if (notify)
			{
				clientThread.invoke(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
					"Meteor report failed: " + error + ".", null));
			}
		});
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

	private void restartRefreshTask()
	{
		if (refreshTask != null) refreshTask.cancel(false);
		if (!config.sharingEnabled())
		{
			if (panel != null) SwingUtilities.invokeLater(panel::setDisabled);
			return;
		}
		refreshTask = executor.scheduleWithFixedDelay(this::refreshReports, 0,
			REFRESH_SECONDS, TimeUnit.SECONDS);
	}

	private void refreshReports()
	{
		if (!config.sharingEnabled() || panel == null) return;
		SwingUtilities.invokeLater(panel::setLoading);
		reportClient.list(
			reports ->
			{
				activeReportKeys.clear();
				for (MeteorReport report : reports) activeReportKeys.add(reportKey(report.getWorld(), report.getX(), report.getY(), report.getPlane()));
				SwingUtilities.invokeLater(() -> { if (panel != null) panel.setReports(reports); });
			},
			error -> SwingUtilities.invokeLater(() -> { if (panel != null) panel.setError(error); }));
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

	private void hopToWorld(int worldId)
	{
		WorldResult result = worldService.getWorlds();
		World target = result == null ? null : result.findWorld(worldId);
		if (target == null || target.getPlayers() < 0 || target.getPlayers() >= 1950) return;
		World current = result.findWorld(client.getWorld());
		if (current != null && !current.getTypes().contains(WorldType.PVP) && target.getTypes().contains(WorldType.PVP)) return;
		clientThread.invoke(() ->
		{
			net.runelite.api.World apiWorld = client.createWorld();
			apiWorld.setActivity(target.getActivity());
			apiWorld.setAddress(target.getAddress());
			apiWorld.setId(target.getId());
			apiWorld.setPlayerCount(target.getPlayers());
			apiWorld.setLocation(target.getLocation());
			apiWorld.setTypes(WorldUtil.toWorldTypes(target.getTypes()));
			hopTarget = apiWorld;
			hopAttempts = 0;
		});
	}

	@Provides
	MeteorReporterConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MeteorReporterConfig.class);
	}
}
