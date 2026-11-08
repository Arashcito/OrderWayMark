package com.orderwaymark.olist;

/** Ordinary least squares with an intercept, solved through the normal equations. */
final class LeastSquares {
    private final int k;          // number of coefficients incl. intercept
    private final double[][] xtx;
    private final double[] xty;
    private int n;

    LeastSquares(int features) {
        this.k = features + 1;
        this.xtx = new double[k][k];
        this.xty = new double[k];
    }

    void add(double y, double... x) {
        double[] row = new double[k];
        row[0] = 1;
        System.arraycopy(x, 0, row, 1, x.length);
        for (int i = 0; i < k; i++) {
            xty[i] += row[i] * y;
            for (int j = 0; j < k; j++) xtx[i][j] += row[i] * row[j];
        }
        n++;
    }

    int rows() {
        return n;
    }

    /** Coefficients [intercept, b1, b2, ...]. */
    double[] solve() {
        if (n < k) throw new IllegalStateException("need at least " + k + " rows, got " + n);
        double[][] a = new double[k][k + 1];
        for (int i = 0; i < k; i++) {
            System.arraycopy(xtx[i], 0, a[i], 0, k);
            a[i][k] = xty[i];
        }
        for (int c = 0; c < k; c++) { // Gaussian elimination with partial pivoting
            int p = c;
            for (int r = c + 1; r < k; r++) if (Math.abs(a[r][c]) > Math.abs(a[p][c])) p = r;
            double[] t = a[c]; a[c] = a[p]; a[p] = t;
            if (Math.abs(a[c][c]) < 1e-12) throw new IllegalStateException("singular system");
            for (int r = 0; r < k; r++) {
                if (r == c) continue;
                double f = a[r][c] / a[c][c];
                for (int j = c; j <= k; j++) a[r][j] -= f * a[c][j];
            }
        }
        double[] b = new double[k];
        for (int i = 0; i < k; i++) b[i] = a[i][k] / a[i][i];
        return b;
    }
}
