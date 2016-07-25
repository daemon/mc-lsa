package net.rocketeer.mclsa;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

public class SimilarityMatrix {
  private final RealMatrix simMatrix;

  public SimilarityMatrix(RealMatrix matrix) {
    RealMatrix simMatrix = new Array2DRowRealMatrix(matrix.getColumnDimension(), matrix.getColumnDimension());
    for (int i = 0; i < matrix.getColumnDimension(); ++i)
      simMatrix.setColumnVector(i, similarityVector(matrix, matrix.getColumnVector(i)));
    this.simMatrix = simMatrix;
  }

  public RealMatrix matrix() {
    return this.simMatrix;
  }

  public static double cosineDistance(RealVector a, RealVector b) {
    if (a.getNorm() < 0.001 || b.getNorm() < 0.001)
      return 0;
    return a.dotProduct(b) / (a.getNorm() * b.getNorm());
  }

  public static RealVector similarityVector(RealMatrix matrix, RealVector vector) {
    RealVector simVec = new ArrayRealVector(matrix.getColumnDimension());
    for (int i = 0; i < simVec.getDimension(); ++i)
      simVec.setEntry(i, cosineDistance(matrix.getColumnVector(i), vector));
    return simVec;
  }
}
