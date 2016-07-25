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

import java.util.concurrent.CompletableFuture;

public class LsaPlugin extends JavaPlugin {
  private RealMatrix structureBlockMatrix;
  private volatile LowRankApproximation lra;
  @Override
  public void onEnable() {

  }

  private class ComputeCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
      CompletableFuture.supplyAsync(() -> {
        int nColumns = Math.max(1, structureBlockMatrix.getColumnDimension() / 3);
        return new LowRankApproximation(structureBlockMatrix, nColumns);
      }).thenAccept((approximation) -> {
        lra = approximation;
      });
      Bukkit.getScheduler().runTaskAsynchronously(LsaPlugin.this, () -> {
        lra = new LowRankApproximation(structureBlockMatrix, Math.max(structureBlockMatrix.getColumnDimension() / 3, 1));
        Bukkit.getScheduler().runTask(LsaPlugin.this, () -> {
          commandSender.sendMessage("Completed computing LRA");
        });
      });
      return true;
    }
  }

  private class AddCommand implements CommandExecutor {
    private final WorldEditPlugin worldEdit;
    public AddCommand(WorldEditPlugin worldEdit) {
      this.worldEdit = worldEdit;
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
      Player player = (Player) commandSender;
      CuboidSelection selection = (CuboidSelection) this.worldEdit.getSelection(player);
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
      return true;
    }
  }
}
