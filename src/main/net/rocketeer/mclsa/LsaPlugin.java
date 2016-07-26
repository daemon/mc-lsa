package net.rocketeer.mclsa;

import com.google.gson.Gson;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.bukkit.selections.CuboidSelection;
import org.apache.commons.math3.linear.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LsaPlugin extends JavaPlugin {
  private RealMatrix structureBlockMatrix;
  private volatile LowRankApproximation lra;
  private List<String> names = new ArrayList<>();
  private WorldEditPlugin worldEditPlugin;

  @Override
  public void onEnable() {
    this.worldEditPlugin = (WorldEditPlugin) Bukkit.getPluginManager().getPlugin("WorldEdit");
    this.getCommand("lsaadd").setExecutor(new AddStructureCommand());
    this.getCommand("lsacompute").setExecutor(new ComputeCommand());
    this.getCommand("lsaprint").setExecutor(new InfoCommand());
    this.getCommand("lsasave").setExecutor(new SaveCommand());
    this.getCommand("lsaload").setExecutor(new LoadCommand());
    this.getCommand("lsaquery").setExecutor(new QueryCommand());
  }

  private Map<Integer, Integer> computeBlockDistribution(Player player) {
    CuboidSelection selection = (CuboidSelection) worldEditPlugin.getSelection(player);
    Location minPoint = selection.getMinimumPoint();
    Location maxPoint = selection.getMaximumPoint();
    Map<Integer, Integer> blockIdToCount = new HashMap<>();
    for (int i = minPoint.getBlockX(); i <= maxPoint.getBlockX(); ++i)
      for (int j = minPoint.getBlockY(); j <= maxPoint.getBlockY(); ++j)
        for (int k = minPoint.getBlockZ(); k <= maxPoint.getBlockZ(); ++k) {
          int id = player.getWorld().getBlockAt(i, j, k).getType().getId();
          if (id >= 448 || id == 0) // ignore air and discs
            continue;
          if (!blockIdToCount.containsKey(id))
            blockIdToCount.put(id, 0);
          blockIdToCount.put(id, blockIdToCount.get(id) + 1);
        }
    return blockIdToCount;
  }

  private static void normalize(RealMatrix matrix) {
    for (int i = 0; i < matrix.getColumnDimension(); ++i) {
      RealVector vec = matrix.getColumnVector(i);
      if (vec.getNorm() < 0.0001)
        continue;
      vec = vec.mapMultiply(1 / vec.getNorm());
      matrix.setColumnVector(i, vec);
    }
  }

  private class QueryCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
      Player player = (Player) commandSender;
      RealVector vec = new OpenMapRealVector(448);
      computeBlockDistribution(player).forEach(vec::setEntry);
      vec = SimilarityMatrix.similarityVector(lra.approximation(), vec);
      StringBuilder builder = new StringBuilder();
      builder.append("Matches: ");
      for (int i = 0; i < vec.getDimension(); ++i)
        if (Math.abs(vec.getEntry(i)) > 0.6)
          builder.append(String.format("%s (%.3f) ", names.get(i), Math.abs(vec.getEntry(i))));
      if (builder.length() > 10)
        player.sendMessage(builder.toString());
      else
        player.sendMessage("No matches");
      return true;
    }
  }

  private class SaveCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
      Gson gson = new Gson();
      String structMatJson = gson.toJson(structureBlockMatrix.getData());
      getConfig().set("struct-block-mat-data", structMatJson);
      getConfig().set("names-data", names);
      saveConfig();
      return true;
    }
  }

  private class LoadCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
      Gson gson = new Gson();
      double[][] matData = gson.fromJson(getConfig().getString("struct-block-mat-data"), double[][].class);
      structureBlockMatrix = new OpenMapRealMatrix(matData.length, matData[0].length);
      structureBlockMatrix.setSubMatrix(matData, 0, 0);
      names = getConfig().getStringList("names-data");
      return true;
    }
  }

  private class InfoCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
      SimilarityMatrix matrix;
      if (strings.length > 0 && strings[0].equalsIgnoreCase("original"))
        matrix = new SimilarityMatrix(structureBlockMatrix);
      else
        matrix = new SimilarityMatrix(lra.approximation());
      lra.approximation().walkInRowOrder(new PrettyPrintingMatrixVisitor("Low-rank Approximation", names));
      System.out.println("Rank: " + lra.rank());
      System.out.println(String.format("Relative error: %.6f", structureBlockMatrix.subtract(lra.approximation()).getFrobeniusNorm() /
        structureBlockMatrix.getFrobeniusNorm()));
      matrix.matrix().walkInRowOrder(new PrettyPrintingMatrixVisitor("Similarity Matrix", names));
      commandSender.sendMessage("Printed info to console.");
      return true;
    }
  }

  private class ComputeCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
      commandSender.sendMessage("Calculating LRA...");
      Bukkit.getScheduler().runTaskAsynchronously(LsaPlugin.this, () -> {
        int rank = new RRQRDecomposition(structureBlockMatrix).getRank(0.0001);
        lra = new LowRankApproximation(structureBlockMatrix, (int) Math.max(rank / 2.5, 1));
        Bukkit.getScheduler().runTask(LsaPlugin.this, () -> {
          commandSender.sendMessage("Calculating LRA complete");
        });
      });
      return true;
    }
  }

  private class AddStructureCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] args) {
      String name = args[0];
      names.add(name);
      if (structureBlockMatrix == null)
        structureBlockMatrix = new OpenMapRealMatrix(448, 1);
      else {
        RealMatrix oldMatrix = structureBlockMatrix;
        structureBlockMatrix = new OpenMapRealMatrix(448, structureBlockMatrix.getColumnDimension() + 1);
        structureBlockMatrix.setSubMatrix(oldMatrix.getData(), 0, 0);
      }
      int col = structureBlockMatrix.getColumnDimension() - 1;
      Player player = (Player) commandSender;
      computeBlockDistribution(player).forEach((row, count) -> structureBlockMatrix.setEntry(row, col, count));
      commandSender.sendMessage("Structure added");
      normalize(structureBlockMatrix); // TODO make more efficient
      return true;
    }
  }
}