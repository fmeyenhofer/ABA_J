package img;

import mpicbg.spim.data.SpimDataException;
import net.imagej.ImgPlus;

import net.imglib2.Dimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.InvertibleRealTransformSequence;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;
import rest.AllenRefVol;
import rest.Atlas;

/**
 * Allen Reference Atlas (ARA) ImgPlus.
 * holds additionally a mapping that allows to map this image
 * onto the ARA or, conversely, map any data from the ARA onto this image.
 *
 * @author Felix Meyenhofer
 */
public class AraImgPlus<T extends RealType<T>> extends ImgPlus<T> {

    /** plane along which the section image is cut */
    private  Atlas.PlaneOfSection plane;

    /** Thin plate splines (2D) */
    private InvertibleRealTransform tps;

    /** Mapping of the section image in the global coordinates */
    private AffineTransform3D Ts;

    /** Mapping of reference volume to global coordinates (could contain a manual transformation) */
    private AffineTransform3D Tr;
    
    /** Plane definition */
    private double[] u; // 2. Richtungsvektor
    private double[] v; // 2. Richtungsvektor
    private double[] p; // Stützvektor


    public AraImgPlus(Img<T> img, Atlas.PlaneOfSection planeOfSection, double scale) {
        super(img);

        this.plane = planeOfSection;
        this.Ts = new AffineTransform3D();
        this.Ts.scale(scale);
    }

    public AraImgPlus(Img<T> img,
                      InvertibleRealTransform tps,
                      AffineTransform3D Ts,
                      AffineTransform3D Tr,
                      double[] u, double[] v, double[] p) {
        super(img);
        this.tps = tps;
        this.Ts = Ts;
        this.Tr = Tr;
        this.u = u;
        this.v = v;
        this.p = p;
    }

    public RandomAccessibleInterval<T> createSectionVolume(AllenRefVol targetVolume) throws SpimDataException {
        int d = plane.getFixedDimension();

        AffineTransform3D t = targetVolume.getTransform();
        double sz = t.get(d, d);
        Ts.set(sz, d, d);

        Dimensions dims = targetVolume.getDimensions();
        long maxD = dims.dimension(d)-1;

        RandomAccessibleInterval secVol = Views.addDimension(getImg(), 0, maxD);
        if (plane.swapAxes()) {
            return Views.permute(secVol, d, plane.getSectionAxes()[0]);
        } else {
            return secVol;
        }
    }

    public double[] getAraCoordinate(double x, double y) {
        double[] xy1 = new double[]{x, y};
        double[] xy2 = new double[2];
        tps.apply(xy1, xy2);

        double s = xy2[0];
        double t = xy2[1];
        double[] lPos = new double[]{
                p[0] + s * u[0] + t * v[0],
                p[1] + s * u[1] + t * v[1],
                p[2] + s * u[2] + t * v[2]
        };

        InvertibleRealTransformSequence T = new InvertibleRealTransformSequence();
        T.add(Ts);
        T.add(Tr.copy().inverse());

        double[] gPos = new double[3];
        T.apply(lPos, gPos);

        return gPos;
    }

    public AffineTransform3D getSectionTransform() {
        return this.Ts;
    }

    public boolean hasSectionTransform() {
        return this.Ts != null;
    }

    public static void main(String[] args) {
//        InvertibleRealTransformSequence sequence = new InvertibleRealTransformSequence();
//
//        Img img = null;
//        AraImgPlus ara = new AraImgPlus(img, sequence);
    }
}
