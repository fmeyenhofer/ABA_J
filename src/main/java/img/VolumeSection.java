package img;

import net.imglib2.realtransform.AffineTransform3D;
import rest.Atlas;

/**
 * Class defining a plane in a (template) volume
 *
 * @author Felix Meyenhofer
 */
public class VolumeSection {

    private double[] p;
    private double[] v;
    private double[] u;

    public VolumeSection(double[] u, double[] v, double[] p) {
        this.u = u;
        this.v = v;
        this.p = p;
    }

    public VolumeSection(Atlas.PlaneOfSection planeOfSection, Integer sectionNumber) {
        int[] axes = planeOfSection.getSectionAxesIndices();

        this.u = new double[3];
        this.v = new double[3];
        this.p = new double[3];
        for (int axis : axes) {
            this.u[axis] = 1;
            this.v[axis] = 1;
        }

        this.p[planeOfSection.getFixedAxisIndex()] = sectionNumber;
    }

    public double[] getP() {
        return p;
    }

    public double[] getV() {
        return v;
    }

    public double[] getU() {
        return u;
    }

    public double[] getSectionCoordinates(double[] coordinate) {
        return getSectionCoordinates(coordinate[0], coordinate[1]);
    }

    public double[] getSectionCoordinates(double s, double t) {
        return new double[]{
                p[0] + s * u[0] + t * v[0],
                p[1] + s * u[1] + t * v[1],
                p[2] + s * u[2] + t * v[2]
        };
    }

    public void applyTransform(AffineTransform3D t) {
        double[] ut = new double[3];
        double[] vt = new double[3];
        double[] pt = new double[3];

        t.apply(u, ut);
        t.apply(v, vt);
        t.apply(p, pt);

        this.u = ut;
        this.v = vt;
        this.p = pt;
    }
}
