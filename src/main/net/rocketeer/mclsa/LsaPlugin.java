package net.rocketeer.mclsa;

import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.bukkit.selections.CuboidSelection;
import org.apache.commons.math3.linear.OpenMapRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class LsaPlugin extends JavaPlugin {
  private RealMatrix structureBlockMatrix;
  private volatile LowRankApproximation lra;
  @Override
  public void onEnable() {
    WorldEditPlugin worldEditPlugin = (WorldEditPlugin) Bukkit.getPluginManager().getPlugin("WorldEdit");
    this.getCommand("add").setExecutor(new AddStructureCommand(worldEditPlugin));
    this.getCommand("compute").setExecutor(new ComputeCommand());
    this.getCommand("printinfo").setExecutor(new InfoCommand());
  }

  private class InfoCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
      SimilarityMatrix matrix;
      if (strings.length > 0 && strings[0].equalsIgnoreCase("original"))
        matrix = new SimilarityMatrix(structureBlockMatrix);
      else
        matrix = new SimilarityMatrix(lra.approximation());
      lra.approximation().walkInRowOrder(new PrettyPrintingMatrixVisitor("Low-rank Approximation"));
      System.out.println(String.format("Relative error: %.2f", structureBlockMatrix.subtract(lra.approximation()).getFrobeniusNorm() /
        structureBlockMatrix.getFrobeniusNorm()));
      matrix.matrix().walkInRowOrder(new PrettyPrintingMatrixVisitor("Similarity Matrix"));
      commandSender.sendMessage("Printed info to console.");
      return true;
    }
  }

  private class ComputeCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
      commandSender.sendMessage("Calculating LRA...");
      Bukkit.getScheduler().runTaskAsynchronously(LsaPlugin.this, () -> {
        lra = new LowRankApproximation(structureBlockMatrix, Math.max(structureBlockMatrix.getColumnDimension() / 3, 1));
        Bukkit.getScheduler().runTask(LsaPlugin.this, () -> {
          commandSender.sendMessage("Calculating LRA complete");
        });
      });
      return true;
    }
  }

  private class AddStructureCommand implements CommandExecutor {
    private final WorldEditPlugin worldEdit;
    public AddStructureCommand(WorldEditPlugin worldEdit) {
      this.worldEdit = worldEdit;
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
      Player player = (Player) commandSender;
      CuboidSelection selection = (CuboidSelection) this.worldEdit.getSelection(player);
      if (selection == null) {
        commandSender.sendMessage("You need to make a selection using WorldEdit first!");
      }
      if (structureBlockMatrix == null)
        structureBlockMatrix = new OpenMapRealMatrix(448, 1);
      else {
        RealMatrix oldMatrix = structureBlockMatrix;
        structureBlockMatrix = new OpenMapRealMatrix(448, structureBlockMatrix.getColumnDimension() + 1);
        structureBlockMatrix.setSubMatrix(oldMatrix.getData(), 0, 0);
      }
      int col = structureBlockMatrix.getColumnDimension() - 1;
      Location minPoint = selection.getMinimumPoint();
      Location maxPoint = selection.getMaximumPoint();
      for (int i = minPoint.getBlockX(); i <= maxPoint.getBlockX(); ++i)
        for (int j = minPoint.getBlockY(); j <= maxPoint.getBlockY(); ++j)
          for (int k = minPoint.getBlockZ(); k <= maxPoint.getBlockZ(); ++k) {
            int row = player.getWorld().getBlockAt(i, j, k).getType().getId();
            if (row >= 448 || row == 0) // ignore air and discs
              continue;
            structureBlockMatrix.addToEntry(row, col, 1);
          }
      commandSender.sendMessage("Structure added");
      return true;
    }
  }
}