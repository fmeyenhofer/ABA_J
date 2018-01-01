package img.ij2;

import io.scif.img.IO;
import net.imagej.ImageJ;
import net.imglib2.Cursor;
import net.imglib2.Dimensions;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.region.hypersphere.HyperSphere;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.logic.BitType;
import net.imglib2.view.Views;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.cpu.nativecpu.NDArray;
import org.nd4j.linalg.dimensionalityreduction.PCA;
import org.nd4j.linalg.factory.Nd4j;

import java.io.IOException;
import java.util.*;

/**
 * This class generates a ordered set of points on a outline/contour.
 *
 * It computes the center and rotation with PCA on the coordinates. Then it computes the intersections
 * of the contour with the principal axis. The axis intersection point are the starting set
 * to iteratively compute triangles and find the corresponding point on the outline.
 *
 * Similar outlines can be matches by generating the correspondences with this class.
 *
 * @author Felix Meyenhofer
 */
public class SectionImageOutlineCorrespondence {

    /** Contour coordinates */
    private final INDArray O;
    private final int rows;
    private final int cols = 2;

    /** Centroid coordinates of the controur */
    private double cx;
    private double cy;

    /** Rotation angle of the first principal axis */
    private double theta;

    /** Tolerance on the polar coordinate angle of the outline candidate */
    private double phi_tol = 0.1;


    /**
     * Constructor
     *
     * @param matrix contour/outline coordinates. N by 2 matrix
     */
    public SectionImageOutlineCorrespondence(INDArray matrix) {
        O = matrix;
        rows = matrix.shape()[0];

        INDArray centroid = O.mean(0);
        cx = centroid.getDouble(0, 0);
        cy = centroid.getDouble(0, 1);

        INDArray A = O.dup();
        INDArray factors = PCA.pca_factor(A, cols, true);
        double n1y = factors.getDouble(0, 0);
        double n1x = factors.getDouble(0, 1);

        theta = -Math.atan(n1x / n1y);
    }

    /**
     * Constructor
     *
     * @param outline binary mask of the some object outline.
     */
    public SectionImageOutlineCorrespondence(RandomAccessibleInterval<BitType> outline) {
        this(getOutlineCoordinates(outline));
    }

    /**
     * Get all the (true) coordinates of a given binary mask
     *
     * @param outline binary object mask
     * @return outline coordinates
     */
    private static INDArray getOutlineCoordinates(RandomAccessibleInterval<BitType> outline) {
        float[] position = new float[outline.numDimensions()];
        List<INDArray> coordinates = new ArrayList<>();

        Cursor<BitType> cursor = Views.flatIterable(outline).cursor();
        while (cursor.hasNext()) {
            BitType value = cursor.next();
            if (value.get()) {
                cursor.localize(position);
                coordinates.add(Nd4j.create(position));
            }
        }

        return new NDArray(coordinates, new int[]{coordinates.size(), position.length});
    }

    /**
     * Generate a set of ordered correspondence points.
     *
     * @param levels number of sampling levels (1 -> 8 points, 2 -> 16 points etc.)
     * @return correspondence points coordinates. 4*2^levels by 2 matrix
     */
    public int[][] generatePoints(int levels) {
        TreeSet<OutlinePoint> outline = new TreeSet<>();

        // Transform coordinates and put them a tree-set ordered by the radial distribution around the center
        for (int r = 0; r < rows; r++) {
            double x = O.getRow(r).getDouble(0);
            double y = O.getRow(r).getDouble(1);

            double xc = x - cx;
            double yc = y - cy;

            double u = xc * Math.cos(theta) - yc * Math.sin(theta);
            double v = xc * Math.sin(theta) + yc * Math.cos(theta);

            outline.add(new OutlinePoint(u, v, r));
        }

        // Find the principal axis intersections
        TreeSet<OutlinePoint> correspondences = new TreeSet<>();

        List<OutlinePoint> subset = getOutlineSubset(outline, -Math.PI, -Math.PI + phi_tol);
        subset.addAll(getOutlineSubset(outline, Math.PI - phi_tol, Math.PI));
        correspondences.add(findClosestToXAxis(subset));

        subset.clear();
        subset.addAll(getOutlineSubset(outline, -Math.PI / 2 - phi_tol, -Math.PI / 2 + phi_tol));
        correspondences.add(findClosestToYAxis(subset));

        subset.clear();
        subset.addAll(getOutlineSubset(outline, - phi_tol, phi_tol));
        correspondences.add(findClosestToXAxis(subset));

        subset.clear();
        subset.addAll(getOutlineSubset(outline, Math.PI / 2 - phi_tol,Math.PI / 2 + phi_tol));
        correspondences.add(findClosestToYAxis(subset));
//        printPoints(correspondences);

        // Create correspondence points
        int l = 1;
        while (l <= levels) {
            List<OutlinePoint> collector = new ArrayList<>();

            // Triangulate over the existing points
            for (OutlinePoint p1 : correspondences) {
                subset.clear();
                OutlinePoint p2 = correspondences.higher(p1);
                if (p2 == null) {
                    p2 = correspondences.first();
                    subset.addAll(getOutlineSubset(outline, p1.phi - phi_tol, Math.PI));
                    subset.addAll(getOutlineSubset(outline, -Math.PI, p2.phi + phi_tol));
                } else {
                    subset.addAll(getOutlineSubset(outline, p1.phi - phi_tol, p2.phi + phi_tol));
                }

                OutlinePoint x = outlineTriangulation(subset, p1, p2);
                collector.add(p1);
                collector.add(x);
            }

            l++;
            correspondences.clear();
            correspondences.addAll(collector);
        }
//        printPoints("correspondences", correspondences);

        int[][] P = new int[correspondences.size()][cols];
        int i = 0;
        for (OutlinePoint point : correspondences) {
            P[i][0] = O.getRow(point.index).getInt(0);
            P[i][1] = O.getRow(point.index).getInt(1);
            i++;
        }

        return P;
    }

    /**
     * For debuggin.
     *
     * @param prefix reference prefixed to all points
     * @param points collection of points
     */
    private void printPoints(String prefix,  Collection<OutlinePoint> points) {
        for (OutlinePoint point : points) {
            System.out.println(prefix + ": i=" + point.index + ", phi=" + point.phi + ", x=" + point.x + ", y=" + point.y);
        }
    }

    /**
     * Get a subset of outline coordinates filtering them by their radial polar coordinate
     *
     * @param outline outline coordinates
     * @param lb lower bound of the radial coordinate
     * @param ub upper bound of the radial coordinate
     * @return return a coordinate subset within the radial coordinate bounds
     */
    private List<OutlinePoint> getOutlineSubset(TreeSet<OutlinePoint> outline, double lb, double ub) {
        List<OutlinePoint> subset = new ArrayList<>();
        for (OutlinePoint point: outline) {
            if (lb < point.phi) {
                if (point.phi < ub) {
                    subset.add(point);
                } else {
                    break;
                }
            }
        }

        return subset;
    }

    /**
     * Find the closest coordinate to the x-axis
     *
     * @param points coordinates to search
     * @return the closest point to the x-axis
     */
    private OutlinePoint findClosestToXAxis(List<OutlinePoint> points) {
        OutlinePoint closest = points.get(0);

        for (OutlinePoint point : points.subList(1, points.size()-1)) {
            if (Math.abs(point.y) < Math.abs(closest.y)) {
                closest = point;
            }
        }

        return closest;
    }

    /**
     * Find the closest Coordinate to the y-axis
     *
     * @param points coordinates to search for
     * @return closest point to the x-axis
     */
    private OutlinePoint findClosestToYAxis(List<OutlinePoint> points) {
        OutlinePoint closest = points.get(0);

        for (OutlinePoint point : points.subList(1, points.size()-1)) {
            if (Math.abs(point.x) < Math.abs(closest.x)) {
                closest = point;
            }
        }

        return closest;
    }

    /**
     * Triangulate between between p1 and p2 finding the best possible match
     * among the points
     *
     * @param points coordinate of the outline stretch between p1 and p2
     * @param p1 starting point
     * @param p2 ending point
     * @return triangulated point
     */
    private OutlinePoint outlineTriangulation(List<OutlinePoint> points, OutlinePoint p1, OutlinePoint p2) {
        double qx = (p1.x + p2.x) / 2;
        double qy = (p1.y + p2.y) / 2;
        double mpp = (p2.y - p1.y) / (p2.x - p1.x);

        double m_min = Double.MAX_VALUE;
        OutlinePoint res = null;

        for (OutlinePoint point : points) {
            // Check if the qo is perpendicular to the base of q (line p1-p2)
            double mx = (point.y - qy) / (point.x - qx);
            double m = Math.abs(1 + mpp * mx);
            if (m < m_min) {
                m_min = m;
                res = point;
            }
        }
        return res;
    }

    /**
     * Visualisation of the outline/contour and the generated (correspondence) points.
     *
     * @param dims dimensions of the output image
     * @param points ordered set of points
     * @return binary image stack with the contour and the points each on a separate frame
     */
    public Img<BitType> createPointsImage(Dimensions dims, int[][] points) {
        ArrayImgFactory<BitType> factory = new ArrayImgFactory<>();
        Img<BitType> img = factory.create(dims, new BitType());

        RandomAccess randomAccess = img.randomAccess();

        randomAccess.setPosition(new int[]{(int)cx, (int)cy});
        HyperSphere<BitType> sphere = new HyperSphere<>(img, randomAccess, 3);
        for (BitType pixel : sphere) {
            pixel.set(true);
        }

        for (int[] point : points) {
            randomAccess.setPosition(point);

            sphere = new HyperSphere<>(img, randomAccess, 3);
            for (BitType pixel : sphere) {
                pixel.set(true);
            }
        }

        return img;
    }


    /**
     * Helper class to organise coordinates, original index and
     * radial coordinate
     */
    private class OutlinePoint implements Comparable<OutlinePoint>{
        double x;
        double y;
        Double phi;
        int index;

        OutlinePoint(double x, double y, int index) {
            this.x = x;
            this.y = y;
            this.phi = Math.atan2(y, x);
            this.index = index;
        }

        @Override
        public int compareTo(OutlinePoint o) {
            return phi.compareTo(o.phi);
        }
    }

    /**
     * Get the tolerance on the upper and lower bounds of the radial
     * coordinate for constraining the search space. (in radian)
     *
     * @return radial coordinate tolerance
     */
    public double getRadialCoordinateTolerance() {
        return phi_tol;
    }

    /**
     * Set the tolerance on the upper and lower bounds of the radial
     * coordinate for constraining the search space
     *
     * @param phi_tol radial coordinate tolerance in radian
     */
    public void setRadialCoordinateTolerance(double phi_tol) {
        this.phi_tol = phi_tol;
    }


    /**
     * Functionality Testing
     *
     * @param args not used
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        ImageJ ij = new ImageJ();
        ij.ui().showUI();

        String path = "/Users/turf/switchdrive/SJMCS/data/devel/masks/section-mask-outline.tif";

        BitType type = new BitType();
        ArrayImgFactory<BitType> factory = new ArrayImgFactory<>();
        RandomAccessibleInterval<BitType> out = IO.openImgs(path, factory, type).get(0);

        SectionImageOutlineCorrespondence sampler = new SectionImageOutlineCorrespondence(out);
        int[][] cor = sampler.generatePoints(4);
        RandomAccessibleInterval<BitType> pts = sampler.createPointsImage(out, cor);

        RandomAccessibleInterval<BitType> stack = Views.stack(out, pts);
//        ImageJFunctions.show(stack);
        ij.ui().show(stack);

        System.out.println("Done");
    }
}
