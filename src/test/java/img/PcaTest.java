package img;

import ij.gui.Plot;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.stat.correlation.Covariance;

//import org.nd4j.linalg.api.ndarray.INDArray;
//import org.nd4j.linalg.cpu.nativecpu.NDArray;
//import org.nd4j.linalg.dimensionalityreduction.PCA;
//import org.nd4j.linalg.factory.Nd4j;


import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * @author Felix Meyenhofer
 */
public class PcaTest {

    public static void main(String[] args) throws URISyntaxException, IOException {

        URL url = PcaTest.class.getResource("/contour-coords.txt");
        File file = new File(url.toURI());
        FileInputStream inputStream = new FileInputStream(file.getAbsolutePath());
        String content = IOUtils.toString(inputStream);

        String[] lines = content.split(System.lineSeparator());
        double[][] points = new double[lines.length][2];
        double[] x = new double[lines.length];
        double[] y = new double[lines.length];
        int i = 0;
        for (String line : lines) {
//            System.out.println(line);
            String[] parts = line.split(" ");
            x[i] = Double.parseDouble(parts[0]);
            y[i] = Double.parseDouble(parts[1]);
            points[i][0] = x[i];
            points[i][1] = y[i];
            i++;
        }

        Plot plot = new Plot("coords", "x", "y",y, x);
        plot.show();

//        INDArray ndArray = Nd4j.create(points);
//        INDArray factors = PCA.pca_factor(ndArray, 2, true);
//        System.out.println(factors.getDouble(0, 0));
//        System.out.println(factors.getDouble(0, 1));


//        //create points in a double array
//        double[][] pointsArray = new double[][] {
//                new double[] { -1.0, -1.0 },
//                new double[] { -1.0, 1.0 },
//                new double[] { 1.0, 1.0 } };

        //create real matrix
        RealMatrix realMatrix = MatrixUtils.createRealMatrix(points);

        //create covariance matrix of points, then find eigen vectors
        //see https://stats.stackexchange.com/questions/2691/making-sense-of-principal-component-analysis-eigenvectors-eigenvalues

        Covariance covariance = new Covariance(realMatrix);
        RealMatrix covarianceMatrix = covariance.getCovarianceMatrix();
        EigenDecomposition ed = new EigenDecomposition(covarianceMatrix);



//        System.out.println(ed.toString());
        System.out.println(ArrayUtils.toString(ed.getV().getEntry(0,0)));
        System.out.println(ArrayUtils.toString(ed.getV().getEntry(0,1)));


        System.out.println("");
        System.out.println(ArrayUtils.toString(ed.getV()));
        System.out.println(ArrayUtils.toString(ed.getVT()));
//        System.out.println(ArrayUtils.toString(ed.getD()));
//        System.out.println(ArrayUtils.toString(ed.getD().getEntry(0,0)));
//        System.out.println(ArrayUtils.toString(ed.getD().getEntry(0,1)));
    }
}
