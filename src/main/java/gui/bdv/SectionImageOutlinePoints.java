package gui.bdv;

import bdv.util.BdvOverlay;
import img.SectionImageOutlineSampler;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.type.numeric.ARGBType;
import rest.AllenRefVol;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Class to visualize the section outline points in {@link bdv.BigDataViewer}
 *
 * @author Felix Meyenhofer
 */
public class SectionImageOutlinePoints extends BdvOverlay{

    private static int MAX_POINT_SIZE = 10;

    private final List<double[]> points;
    private boolean depthDependentScaling;

    public SectionImageOutlinePoints(double[] centroid, double[] first, double missingCoord, AllenRefVol.Plane p) {
        points = new ArrayList<>(2);
        switch (p) {
            case YZ:
                points.add(new double[]{missingCoord, centroid[1], centroid[0]});
                points.add(new double[]{missingCoord, first[1], first[0]});
                break;

            default:
                throw new RuntimeException("bla");
        }

        depthDependentScaling = false;
    }

    public SectionImageOutlinePoints(double[] centroid, double[] first, InvertibleRealTransform t) {
        double[] cVPos = new double[]{centroid[0], centroid[1], 0};
        double[] cLPos = new double[3];
        t.apply(cVPos, cLPos);

        double[] fVPos = new double[]{first[0], first[1], 0};
        double[] fLPos = new double[3];
        t.apply(fVPos, fLPos);

        points = new ArrayList<>(2);
        points.add(cLPos);
        points.add(fLPos);

        depthDependentScaling = true;
    }

    public SectionImageOutlinePoints(List<SectionImageOutlineSampler.OutlinePoint> outlinePoints,
                                     double missingCoord,
                                     AllenRefVol.Plane p) {
        points = new ArrayList<>(outlinePoints.size());
        switch (p) {
            case YZ:
                for (SectionImageOutlineSampler.OutlinePoint pt : outlinePoints) {
                    points.add(new double[]{missingCoord, pt.getCoordinates()[1], pt.getCoordinates()[0]});
                }
                break;

            default:
                throw new RuntimeException("bla");
        }

        depthDependentScaling = false;
    }

    public SectionImageOutlinePoints(List<SectionImageOutlineSampler.OutlinePoint> outlinePoints, InvertibleRealTransform t) {
        points = new ArrayList<>(outlinePoints.size());
        for (SectionImageOutlineSampler.OutlinePoint pt : outlinePoints) {
            double[] lPos = new double[3];
            double[] vPos = new double[]{pt.getCoordinates()[0], pt.getCoordinates()[1], 0};
            t.apply(vPos, lPos);
            points.add(lPos);
        }

        depthDependentScaling = true;
    }

    @Override
    protected void draw(Graphics2D graphics) {
        final AffineTransform3D t = new AffineTransform3D();
        getCurrentTransform3D(t);

        final double[] gPos = new double[3];
        for (final double[] lPos : points) {
            t.apply(lPos, gPos);

            final int wh = getSize(gPos[2]);
            final int x = (int) (gPos[0] - 0.5 * wh);
            final int y = (int) (gPos[1] - 0.5 * wh);

            graphics.setColor(getColor(gPos[2]));
            graphics.fillOval(x, y, wh, wh);
        }
    }

    private Color getColor(final double depth) {
        final int color = info.getColor().get();

        int alpha = 255 - (int) Math.round(Math.abs(depth));
        if (alpha < 64) {
            alpha = 64;
        }

        return new Color(ARGBType.red(color), ARGBType.green(color), ARGBType.blue(color), alpha);
    }

    private int getSize(final double depth) {
        if (depthDependentScaling) {
            final double range = info.getDisplayRangeMax();
            return (int) Math.max(1, MAX_POINT_SIZE - range * Math.round(Math.abs(depth)));
        } else {
            return MAX_POINT_SIZE;
        }
    }
}
