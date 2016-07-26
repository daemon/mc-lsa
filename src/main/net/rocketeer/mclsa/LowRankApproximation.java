package net.rocketeer.mclsa;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition;

public class LowRankApproximation {
  private final int rank;
  private final SingularValueDecomposition svd;
  private final RealMatrix lowRankApprox;

  public LowRankApproximation(RealMatrix matrix, int rank) {
    this.rank = rank;
    this.svd = new SingularValueDecomposition(matrix);
    RealMatrix sigma = this.svd.getS();
    for (int i = rank; i < sigma.getColumnDimension(); ++i)
      sigma.setEntry(i, i, 0);
    sigma.walkInRowOrder(new PrettyPrintingMatrixVisitor("Sigma"));
    this.lowRankApprox = this.svd.getU().multiply(sigma).multiply(this.svd.getVT());
  }

  public int rank() {
    return this.rank;
  }

  public SingularValueDecomposition svd() {
    return this.svd;
  }

  public RealMatrix approximation() {
    return this.lowRankApprox;
  }
}
