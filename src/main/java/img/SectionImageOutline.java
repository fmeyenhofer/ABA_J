package img;

import rest.Atlas;

import net.imagej.ImageJ;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.region.hypersphere.HyperSphere;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.view.Views;
import io.scif.img.IO;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.stat.correlation.Covariance;

import java.text.DecimalFormat;
import java.text.NumberFormat;
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
 * TODO: It might be worth looking into fitting a contour function to have sub-pixel accuracy (see {@link SectionImageOutline#outlineTriangulation})
 *
 * @author Felix Meyenhofer
 */
@SuppressWarnings("WeakerAccess")
public class SectionImageOutline {

    /** Contour coordinates */
    private final RealMatrix O;
    private final int rows;
    private final int cols;

    /** Centroid coordinates of the contour */
    private double cx;
    private double cy;

    /** Rotation angle of the first principal axis */
    private Double theta;

    /** Tolerance on the polar coordinate angle of the outline candidate */
    private double phi_tol = 0.1;

    /** Number of iteration levels to generate correspondence points. */
    private int lvls;

    /** Ordered samples along the contours */
    private ArrayList<OutlinePoint> samples;


    /**
     * Constructor
     *
     * @param coords contour coordinates
     * @param levels number of triangulation iteration levels
     */
    public SectionImageOutline(List<double[]> coords, int levels) {
        lvls = levels;

        rows = coords.size();
        cols = coords.get(0).length;

        O = MatrixUtils.createRealMatrix(rows, cols);
        double sumX = 0;
        double sumY = 0;
        int i = 0;
        for (double[] coordinate : coords) {
            sumX += coordinate[0];
            sumY += coordinate[1];

            O.setRow(i++, coordinate);
        }

        cx = sumX / rows;
        cy = sumY / rows;
    }

    /**
     * Constructor
     *
     * @param outline contour coordinates
     * @param levels number of triangulation iteration levels
     */
    public SectionImageOutline(RandomAccessibleInterval<BitType> outline, int levels) {
        this(extractOutlineCoordinates(outline), levels);
    }

    /**
     * Do PCA on the outline coordinate values and use
     * the component to compute the rotation angle if the section.
     */
    public void doPca() {
        Covariance covariance = new Covariance(O.copy());
        RealMatrix covarianceMatrix = covariance.getCovarianceMatrix();
        EigenDecomposition ed = new EigenDecomposition(covarianceMatrix);

        double n1x = ed.getV().getEntry(0, 1);
        double n1y = ed.getV().getEntry(0, 0);
        theta = -Math.atan(n1x / n1y);
    }

    /**
     * Bounding box of the input coordinates
     *
     * @param mat input coordinates
     * @return bounding box [x_min y_min x_max y_max]
     */
    public double[] getBoundingBox(RealMatrix mat) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double maxY = Double.MIN_VALUE;
        for (int i = 0; i < mat.getRowDimension(); i++) {
            double x = mat.getEntry(i, 0);
            double y = mat.getEntry(i, 1);
            if (x < minX) {
                minX = x;
            }
            if (x > maxX) {
                maxX = x;
            }
            if (y < minY) {
                minY = y;
            }
            if (y > maxY) {
                maxY = y;
            }
        }

        return new double[]{minX, minY, maxX, maxY};
    }

    /**
     * Get the bounding box of the {@link SectionImageOutline#theta}- rotated
     * coordinates.
     *
     * @return bounding box [x_min y_min x_max y_max]
     */
    public double[] getRotatedBoundingBox() {
        if (theta == null) {
            doPca();
        }

        RealMatrix O_ = MatrixUtils.createRealMatrix(rows, cols);

        for (int r = 0; r < rows; r++) {
            double x = O.getEntry(r, 0);
            double y = O.getEntry(r, 1);

            double u = x * Math.cos(theta) - y * Math.sin(theta);
            double v = x * Math.sin(theta) + y * Math.cos(theta);

            O_.setRow(r, new double[]{u, v});
        }

        return getBoundingBox(O_);
    }

    /**
     * Get all the (true) coordinates of a given binary mask
     *
     * @param outline binary object mask
     * @return outline coordinates
     */
    private static List<double[]> extractOutlineCoordinates(RandomAccessibleInterval<BitType> outline) {
        int d = outline.numDimensions();
        List<double[]> coordinates = new ArrayList<>();
        Cursor<BitType> cursor = Views.flatIterable(outline).cursor();
        while (cursor.hasNext()) {
            BitType value = cursor.next();
            if (value.get()) {
                double[] position = new double[d];
                cursor.localize(position);
                coordinates.add(position);
            }
        }

        return coordinates;
    }

    /**
     * Generate a set of ordered correspondence points.
     *
     * @return correspondence points coordinates. 4*2^levels by 2 matrix
     */
    public ArrayList<OutlinePoint> sample() {
        if (this.theta == null) {
            doPca();
        }

        TreeSet<OutlinePoint> outline = new TreeSet<>();

        // Transform coordinates and put them a tree-set ordered by the radial distribution around the center
        for (int r = 0; r < rows; r++) {
            double x = O.getEntry(r, 0);
            double y = O.getEntry(r, 1);

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
        while (l <= lvls) {
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

        samples = new ArrayList<>(correspondences.size());

        // Get back the original coordinates
        for (OutlinePoint point : correspondences) {
            double[] coord = O.getRow(point.index);
            samples.add(new OutlinePoint(coord, point.index));
        }

        return samples;
    }

//    /**
//     * For debugging.
//     *
//     * @param prefix reference prefixed to all points
//     * @param points collection of points
//     */
//    private void printPoints(String prefix,  Collection<OutlinePoint> points) {
//        for (OutlinePoint point : points) {
//            System.out.println(prefix + ": i=" + point.index + ", phi=" + point.phi + ", x=" + point.x + ", y=" + point.y);
//        }
//    }

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
            double dy = point.y - qy;
            double dx = point.x - qx;

            // q lies on the contour itself.
            if ((Math.abs(dx) < 1) && (Math.abs(dy) < 1)) {
                res = point;
                break;
            }

            // Check if the qo is perpendicular to the base of q (line p1-p2)
            double mx =  dy / dx;
            double m = Math.abs(1 + mpp * mx);
            if (m < m_min) {
                m_min = m;
                res = point;
            }
        }
        return res;
    }

    /**
     * Map the coordinates
     *
     * @param t transformation
     * @param section plane of section
     * @return transformed outline
     */
    public SectionImageOutline map(InvertibleRealTransform t, Atlas.PlaneOfSection section) {
        List<double[]> coords = new ArrayList<>(rows);

        for (int r = 0; r < rows; r++) {
            double[] srcVect = new double[]{O.getEntry(r, 0), O.getEntry(r, 1), 0};
            double[] dstVect = new double[3];
            t.apply(srcVect, dstVect);
            double[] coord = section.template2SectionCoordinate(dstVect);
            coords.add(coord);
        }

        return new SectionImageOutline(coords, this.lvls);
    }

    /**
     * Try greedily to reduce the global distance between the point sets by
     * moving them along the respective outline.
     *
     * TODO: consider using contour curvature as additional optimization criterion
     * TODO: choose a more flexible approach. rotating all the points in the same direction is too broad. -> Maybe constrained local point-by-point optimization?
     *
     * @param soc Outline samples to match
     * @return optimized set of points
     */
    public ArrayList<OutlinePoint> getOptimizedCorrespondencePoints(SectionImageOutline soc) {
        // Center this outline and order them according the radial coordinate
        TreeSet<OutlinePoint> out1 = new TreeSet<>();
        for (int r = 0; r < rows; r++) {
            OutlinePoint p = new OutlinePoint(O.getRow(r), r);
            p.subtract(cx, cy);
            out1.add(p);
        }

        ArrayList<OutlinePoint> pts1 = getSamples();
        ArrayList<OutlinePoint> pts2 = soc.getSamples();

        // Center the sampled outline points
        ArrayList<OutlinePoint> cor1 = new ArrayList<>(pts1.size());
        ArrayList<OutlinePoint> cor2 = new ArrayList<>(pts2.size());

        double dPhi = 0;
        double dR = 0;
        for (int i = 0; i < pts1.size(); i++) {
            OutlinePoint p1 = pts1.get(i).duplicate().subtract(cx, cy);
            OutlinePoint p2 = pts2.get(i).duplicate().subtract(soc.cx, soc.cy);

            dPhi += p1.deltaPhi(p2);
            dR += p1.deltaR2(p2);

            cor1.add(p1);
            cor2.add(p2);
        }

        // Determine the search direction along the outline
        boolean clockwise = dPhi > 1;

        double dRi = 0;
        int iter = 0;
        while (true) {
            OutlinePoint p_;
            double dR_ = 0;
            ArrayList<OutlinePoint> cor1_ = new ArrayList<>(cor1.size());

            for (int i = 0; i < cor1.size(); i++) {
                OutlinePoint p = cor1.get(i);

                if (clockwise) {
                    p_ = out1.higher(p);
                    if (p_ == null) {
                        p_ = out1.first();
                    }
                } else {
                    p_ = out1.lower(p);
                    if (p_ == null) {
                        p_ = out1.last();
                    }
                }

                cor1_.add(p_);
                dR_ += p_.deltaR2(cor2.get(i));
            }

            if (iter == 0) {
                dRi = dR;
            }

            if (dR_ < dR) {
                cor1 = cor1_;
                dR = dR_;
            } else {
                break;
            }

            iter++;
            System.out.println("" + dR);
        }

        System.out.println("Greedy correspondence optimization: " + iter + " iterations. distance sum: " + dRi + "->" + dR);

        // Get back the original coordinates
        ArrayList<OutlinePoint> optimizedSamples = new ArrayList<>(cor1.size());

        for (OutlinePoint point : cor1) {
            optimizedSamples.add(new OutlinePoint(O.getRow(point.index), point.index));
        }

        return optimizedSamples;
    }

    /**
     * In place optimization of this contour against a second
     *
     * @param outlineSampler outline to optimize for
     */
    public void optimize(SectionImageOutline outlineSampler) {
        this.samples = getOptimizedCorrespondencePoints(outlineSampler);
    }

    /**
     * Visualize the outline coordinates, its centroid and the sampled points.
     *
     * @return image stack (one slice for outline and centroid and the second for the sampled points)
     */
    public RandomAccessibleInterval<UnsignedByteType> visualise() {
        double[] bb = getBoundingBox(O);
        long[] dim = new long[]{(long) (bb[0] +bb[2]+1), (long) (bb[1] + bb[3])+1};
        List<RandomAccessibleInterval<UnsignedByteType>> stk = visualise(dim);

        return Views.stack(stk);
    }

    /**
     * Visualize the outline, centroid and sampled points on top of the input image.
     *
     * @param original image
     * @return image stack
     */
    public RandomAccessibleInterval<UnsignedByteType> visualise(Img<UnsignedByteType> original) {
        long[] dim = new long[original.numDimensions()];
        original.dimensions(dim);

        List<RandomAccessibleInterval<UnsignedByteType>> stk = visualise(dim);
        stk.add(original);

        return Views.stack(stk);
    }

    /**
     * Create the outline+centroid slice and another one for the sampled points.
     *
     * @param dims image dimensions
     * @return list of images slices (0: contour, 1: sampled points)
     */
    public List<RandomAccessibleInterval<UnsignedByteType>> visualise(long[] dims) {
        List<RandomAccessibleInterval<UnsignedByteType>> stack = new ArrayList<>();

        // Draw the centroid and the contour
        RandomAccessibleInterval<UnsignedByteType> contour = new ArrayImgFactory<UnsignedByteType>().create(dims, new UnsignedByteType());
        RandomAccess<UnsignedByteType> cursor = contour.randomAccess();
        cursor.setPosition(new int[]{(int)cx, (int)cy});
        HyperSphere<UnsignedByteType> sphere = new HyperSphere<>(contour, cursor, 3);
        for (UnsignedByteType pixel : sphere) {
            pixel.set(255);
        }

        for (int r = 0; r < rows; r++) {
            int x = (int) O.getEntry(r, 0);
            int y = (int) O.getEntry(r, 1);
            cursor.setPosition(new int[]{x, y});
            cursor.get().set(255);
        }

        // Draw the sampled points (the first one a bit ticker)
        RandomAccessibleInterval<UnsignedByteType> samples = new ArrayImgFactory<UnsignedByteType>().create(dims, new UnsignedByteType());
        RandomAccessible<UnsignedByteType> extended = Views.extendZero(samples);
        cursor = extended.randomAccess();
        int radius = 3;
        for (OutlinePoint point : this.samples) {
            cursor.setPosition(point.getIntCoordinates());
            sphere = new HyperSphere<>(extended, cursor, radius);
            for (UnsignedByteType pixel : sphere) {
                pixel.set(255);
            }

            if (radius == 3) {
                radius--;
            }
        }

        stack.add(contour);
        stack.add(Views.interval(extended, samples));

        return stack;
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
     * Get the generated correspondence points or null if {@link SectionImageOutline#sample()}
     * was not yet called.
     *
     * @return correspondence points
     */
    public ArrayList<OutlinePoint> getSamples() {
        if (samples == null) {
            sample();
        }

        ArrayList<OutlinePoint> pts = new ArrayList<>(samples.size());
        for (OutlinePoint pt : samples) {
            pts.add(pt.duplicate());
        }

        return pts;
    }

    /**
     * Get the outline centroid coordinates
     *
     * @return centroid coordinates
     */
    public double[] getCentroidCoordinates() {
        return new double[]{cx, cy};
    }

    /**
     * Get the rotation of principal component to the
     * vertical axis.
     *
     * @return approximate section rotation
     */
    public double getRotation() {
        return theta;
    }

    /**
     * Get the outline x-y positions
     *
     * @return array of outline coordinates
     */
    public RealMatrix getOutlineCoordinates() {
        return O;
    }


    /**
     * Helper class to organise coordinates, original index and
     * radial coordinate
     */
    public class OutlinePoint implements Comparable<OutlinePoint>{
        double x;
        double y;
        double r;
        Double phi;
        int index;

        private NumberFormat twodec = new DecimalFormat("#0.00");

        OutlinePoint(double[] pt, int index) {
            this(pt[0], pt[1], index);
        }

        OutlinePoint(double x, double y, int index) {
            this.x = x;
            this.y = y;
            this.index = index;

            update();
        }

        private void update() {
            this.phi = Math.atan2(y, x);
            this.r = Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2));
        }

        OutlinePoint duplicate() {
            return new OutlinePoint(x, y, index);
        }

        OutlinePoint subtract(double x, double y) {
            this.x -= x;
            this.y -= y;
            update();
            return this;
        }

        double deltaPhi(OutlinePoint p) {
            return phi - p.phi;
        }

        double deltaR2(OutlinePoint p) {
            return Math.pow(p.x - x, 2) + Math.pow(p.y - y, 2);
        }

        public double[] getCoordinates() {
            return new double[]{x, y};
        }

        int[] getIntCoordinates() {
            return new int[]{(int) x, (int) y};
        }

        @Override
        public int compareTo(OutlinePoint o) {
            return phi.compareTo(o.phi);
        }

        @Override
        public String toString() {
            return "index: " + index +", x=" + twodec.format(x) + ", y=" + twodec.format(y) +
                    ", phi=" + twodec.format(phi) + ", r=" + twodec.format(r);
        }
    }


    /**
     * Functionality Testing
     *
     * @param args not used
     */
    public static void main(String[] args) {
        ImageJ ij = new ImageJ();
        ij.ui().showUI();

        String path = "/Users/meyenhof/switchdrive/SJMCS/data/devel/masks/section-mask-outline-2.tif";

        BitType type = new BitType();
        ArrayImgFactory<BitType> factory = new ArrayImgFactory<>();
        Img<BitType> out = IO.openImgs(path, factory, type).get(0);

        SectionImageOutline sampler = new SectionImageOutline(out, 3);
        sampler.sample();
        RandomAccessibleInterval<UnsignedByteType> vis = sampler.visualise();

//        ImgPlus<UnsignedByteType> img = new ImgPlus<UnsignedByteType>(vis, "Contour sampling", new AxisType[]{Axes.X, Axes.Y, Axes.CHANNEL});
        ij.ui().show(vis);

        System.out.println(sampler.getRotation());
        System.out.println("Done");

        System.out.println(ArrayUtils.toString(sampler.getBoundingBox(sampler.getOutlineCoordinates())));
    }
}
