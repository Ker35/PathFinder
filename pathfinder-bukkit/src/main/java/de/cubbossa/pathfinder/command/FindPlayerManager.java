package de.cubbossa.pathfinder.command;

import de.cubbossa.disposables.Disposable;
import de.cubbossa.pathfinder.CommandRegistry;
import de.cubbossa.pathfinder.PathFinder;
import de.cubbossa.pathfinder.PathFinderPlugin;
import de.cubbossa.pathfinder.PathPerms;
import de.cubbossa.pathfinder.graph.GraphEntryNotEstablishedException;
import de.cubbossa.pathfinder.graph.NoPathFoundException;
import de.cubbossa.pathfinder.messages.Messages;
import de.cubbossa.pathfinder.misc.PathPlayer;
import de.cubbossa.pathfinder.navigation.NavigationLocation;
import de.cubbossa.pathfinder.navigation.NavigationModule;
import de.cubbossa.pathfinder.navigation.Route;
import de.cubbossa.pathfinder.node.implementation.PlayerNode;
import de.cubbossa.pathfinder.util.BukkitUtils;
import dev.jorel.commandapi.CommandTree;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public class FindPlayerManager implements Disposable {

  private final Map<UUID, Map<UUID, BukkitTask>> requests;

  public FindPlayerManager(PathFinder pathFinder, CommandRegistry commandRegistry) {

    requests = new HashMap<>();

  }

  private void makeRequest(Player requester, UUID target) {
    if (requester.getUniqueId().equals(target)) {
      BukkitUtils.wrap(requester).sendMessage(Messages.CMD_FINDP_NO_SELF);
      return;
    }
    Player targetPlayer = Bukkit.getPlayer(target);
    if (targetPlayer == null || !targetPlayer.isOnline()) {
      BukkitUtils.wrap(requester).sendMessage(Messages.CMD_FINDP_OFFLINE);
      return;
    }
    if (requests.getOrDefault(target, new HashMap<>()).containsKey(requester.getUniqueId())) {
      BukkitUtils.wrap(requester).sendMessage(Messages.CMD_FINDP_ALREADY_REQ);
      return;
    }

    requests.computeIfAbsent(target, uuid -> new HashMap<>()).put(requester.getUniqueId(),
        Bukkit.getScheduler().runTaskLater(PathFinderPlugin.getInstance(), () -> {
          requests.computeIfPresent(requester.getUniqueId(), (uuid, uuidBukkitTaskMap) -> {
            uuidBukkitTaskMap.remove(requester.getUniqueId());
            BukkitUtils.wrap(requester).sendMessage(Messages.CMD_FINDP_EXPIRED);
            return uuidBukkitTaskMap;
          });
        }, 20 * 30L));
    BukkitUtils.wrap(requester).sendMessage(Messages.CMD_FINDP_REQUEST.formatted(
        Placeholder.parsed("target", targetPlayer.getName()),
        Placeholder.parsed("requester", requester.getName())
    ));
    BukkitUtils.wrap(targetPlayer).sendMessage(Messages.CMD_FINDP_REQUESTED.formatted(
        Placeholder.parsed("target", targetPlayer.getName()),
        Placeholder.parsed("requester", requester.getName())
    ));
  }

  private void acceptRequest(Player target, UUID requester) {
    Map<UUID, BukkitTask> innerRequests = requests.get(target.getUniqueId());
    if (innerRequests == null || !innerRequests.containsKey(requester)) {
      BukkitUtils.wrap(target).sendMessage(Messages.CMD_FINDP_NO_REQ);
      return;
    }
    BukkitTask task = innerRequests.remove(requester);
    task.cancel();

    Player requesterPlayer = Bukkit.getPlayer(requester);
    if (requesterPlayer == null || !requesterPlayer.isOnline()) {
      BukkitUtils.wrap(target).sendMessage(Messages.CMD_FINDP_OFFLINE);
      return;
    }

    BukkitUtils.wrap(target).sendMessage(Messages.CMD_FINDP_ACCEPT.formatted(
        Placeholder.parsed("target", target.getName()),
        Placeholder.parsed("requester", requesterPlayer.getName())
    ));
    BukkitUtils.wrap(requesterPlayer).sendMessage(Messages.CMD_FINDP_ACCEPTED.formatted(
        Placeholder.parsed("target", target.getName()),
        Placeholder.parsed("requester", requesterPlayer.getName())
    ));

    PathPlayer<Player> requesterPathPlayer = BukkitUtils.wrap(requesterPlayer);
    NavigationModule<Player> module = NavigationModule.get();
    module.setFindCommandPath(requesterPathPlayer, Route
        .from(NavigationLocation.movingExternalNode(new PlayerNode(requesterPathPlayer)))
        .to(NavigationLocation.movingExternalNode(new PlayerNode(PathPlayer.wrap(target))))
    ).whenComplete((nav, throwable) -> {
      if (throwable != null) {
        if (throwable instanceof CompletionException) {
          throwable = throwable.getCause();
        }
        if (throwable instanceof NoPathFoundException) {
          requesterPathPlayer.sendMessage(Messages.CMD_FIND_BLOCKED);
        } else if (throwable instanceof GraphEntryNotEstablishedException) {
          requesterPathPlayer.sendMessage(Messages.CMD_FIND_TOO_FAR);
        } else {
          requesterPathPlayer.sendMessage(Messages.CMD_FIND_UNKNOWN);
          PathFinder.get().getLogger().log(Level.SEVERE, "Unknown error while finding path.", throwable);
        }
        return;
      }
      nav.cancelWhenTargetInRange();
    });
  }

  private void declineRequest(Player target, UUID requester) {
    Player requesterPlayer = Bukkit.getPlayer(requester);
    AtomicBoolean wasRemoved = new AtomicBoolean(false);
    requests.computeIfPresent(target.getUniqueId(), (uuid, uuids) -> {
      BukkitTask t = uuids.remove(requester);
      if (t != null) {
        t.cancel();
        wasRemoved.set(true);
      }
      return uuids;
    });
    if (!wasRemoved.get()) {
      BukkitUtils.wrap(target).sendMessage(Messages.CMD_FINDP_NO_REQ);
      return;
    }
    BukkitUtils.wrap(target).sendMessage(Messages.CMD_FINDP_DECLINE.formatted(
        Placeholder.parsed("target", target.getName()),
        Placeholder.parsed("requester", requesterPlayer.getName())
    ));
    BukkitUtils.wrap(requesterPlayer).sendMessage(Messages.CMD_FINDP_DECLINED.formatted(
        Placeholder.parsed("target", target.getName()),
        Placeholder.parsed("requester", requesterPlayer.getName())
    ));
  }
}