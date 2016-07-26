package net.rocketeer.mclsa;

import org.apache.commons.math3.linear.DefaultRealMatrixPreservingVisitor;

import java.util.List;

public class PrettyPrintingMatrixVisitor extends DefaultRealMatrixPreservingVisitor {
  private final String name;
  private List<String> columnLabels;
  private int lastColumn = 0;
  private final StringBuilder builder = new StringBuilder();
  public PrettyPrintingMatrixVisitor(String name) {
    this.name = name;
  }

  public PrettyPrintingMatrixVisitor(String name, List<String> columnLabels) {
    this.columnLabels = columnLabels;
    this.name = name;
  }

  @Override
  public void start(int rows, int columns, int startRow, int endRow, int startColumn, int endColumn) {
    System.out.println(String.format("%s: %d by %d matrix", this.name, rows, columns));
    if (this.columnLabels != null) {
      StringBuilder builder = new StringBuilder();
      for (String label : this.columnLabels)
        builder.append(String.format("%9s", label));
      System.out.println(builder.toString());
    }
  }

  @Override
  public void visit(int row, int column, double value) {
    if (this.lastColumn >= column) {
      System.out.println(this.builder.toString());
      this.builder.setLength(0);
    }
    this.lastColumn = column;
    this.builder.append(String.format("%9.2f", value));
  }

  @Override
  public double end() {
    System.out.println(this.builder.toString());
    return 0;
  }
}
