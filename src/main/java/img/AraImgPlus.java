package img;

import io.AraMapping;
import rest.AllenRefVol;
import rest.Atlas;

import net.imagej.ImgPlus;
import net.imglib2.*;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.img.Img;
import net.imglib2.img.ImgView;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InvertibleRealTransformSequence;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.Views;

import bdv.img.TpsTransformWrapper;
import mpicbg.spim.data.SpimDataException;

/**
 * Allen Reference Atlas (ARA) ImgPlus.
 * holds additionally a mapping that allows to map this image
 * onto the ARA or, conversely, map any data from the ARA onto this image.
 *
 * TODO: make it extend Dataset
 *
 * @author Felix Meyenhofer
 */
@SuppressWarnings("WeakerAccess")
public class AraImgPlus<T extends RealType<T> & NativeType<T>> extends ImgPlus<T> {

    /** plane along which the section image is cut */
    private  Atlas.PlaneOfSection planeOfSection;

    /** resolution of the template the section is registered against **/
    private Atlas.VoxelResolution templateResolution;

    /** Thin plate splines (2D) */
    private TpsTransformWrapper t_tps;
    private TpsTransformWrapper t_tpsi;

    /** Mapping of the section image in the global coordinates */
    private AffineTransform3D t_s;

    /** Mapping of reference volume to global coordinates (could contain a manual transformation) */
    private AffineTransform3D t_r;
    
    /** Plane through the reference volume (in the global space: section--t_s-->global<--t_r--reference) */
    private VolumeSection volumeSection;


    public AraImgPlus(Img<T> img, double scale, Atlas.PlaneOfSection planeOfSection, Atlas.VoxelResolution templateResolution) {
        super(img);

        this.planeOfSection = planeOfSection;
        this.templateResolution = templateResolution;
        this.t_s = new AffineTransform3D();
        this.t_s.scale(scale);
        this.t_r = new AffineTransform3D();
        this.t_r.scale(templateResolution.getValue());
    }

    public AraImgPlus(Img<T> img, AraMapping map) {
        super(img);
        setAraMapping(map);
    }

    public void updateRegistrationInfo(TpsTransformWrapper tps,
                                       TpsTransformWrapper tpsi,
                                       AffineTransform3D Ts,
                                       AffineTransform3D Tr,
                                       VolumeSection plane) {
        this.t_tps = tps;
        this.t_tpsi = tpsi;
        this.t_r = Tr;
        this.t_s = Ts;
        this.volumeSection = plane;
    }

    public void setVolumeSection(VolumeSection volumeSection) {
        this.volumeSection = volumeSection;
    }

    public RandomAccessibleInterval<T> createSectionVolume(AllenRefVol targetVolume) throws SpimDataException {
        int d = planeOfSection.getFixedAxisIndex();

        AffineTransform3D t = targetVolume.getTransform();
        double sz = t.get(d, d);
        t_s.set(sz, d, d);

        Dimensions dims = targetVolume.getDimensions();
        long maxD = dims.dimension(d) - 1;

        RandomAccessibleInterval secVol = Views.addDimension(getImg(), 0, maxD);
        if (planeOfSection.swapAxes()) {
            return Views.permute(secVol, d, planeOfSection.getSectionAxesIndices()[0]);
        } else {
            return secVol;
        }
    }

    public boolean hasSectionNumber() {
        return volumeSection != null;
    }

    public AraMapping getAraMapping() {
        return new AraMapping(planeOfSection, templateResolution, volumeSection, t_s, t_r, t_tps, t_tpsi);
    }

    public void setAraMapping(AraMapping mapping) {
        this.planeOfSection = mapping.getPlaneOfSection();
        this.templateResolution = mapping.getTemplateResolution();
        this.volumeSection = mapping.getVolumeSection();
        this.t_r = mapping.getAffineTr();
        this.t_s = mapping.getAffineTs();
        this.t_tps = mapping.getT_tps();
        this.t_tpsi = mapping.getT_tpsi();
    }

    public double getSectionPosition() {
        double[] p = new double[3];
        t_s.inverse().apply(volumeSection.getP(), p);

        return p[planeOfSection.getFixedAxisIndex()];
    }

    public long getSectionNumber() {
        return Math.round(getSectionPosition());
    }

    /*public double[] getAraCoordinate(double x, double y) {
        double[] xy1 = new double[]{x, y};
        double[] xy2 = new double[2];
        t_tps.apply(xy1, xy2);

        double[] u = volumeSection.getU();
        double[] v = volumeSection.getV();
        double[] p = volumeSection.getP();

        double s = xy2[0];
        double t = xy2[1];
        double[] lPos = new double[]{
                p[0] + s * u[0] + t * v[0],
                p[1] + s * u[1] + t * v[1],
                p[2] + s * u[2] + t * v[2]
        };

        InvertibleRealTransformSequence T = new InvertibleRealTransformSequence();
        T.add(t_s);
        T.add(t_r.copy().inverse());

        double[] gPos = new double[3];
        T.apply(lPos, gPos);

        return gPos;
    }*/

    public double[] getTemplateCoordinate(double[] lPos) {
        double[] dPos = new double[2];
        t_tps.apply(lPos, dPos);

        double[] sPos = planeOfSection.section2TemplateCoordinate(dPos, getSectionPosition());

        double[] tPos = new double[3];
        t_s.apply(sPos, tPos);

        return tPos;
    }

    public Atlas.VoxelResolution getTemplateResolution() {
        return templateResolution;
    }

    public Atlas.PlaneOfSection getPlaneOfSection() {
        return planeOfSection;
    }

    public Img<T> mapSection2Template() {
        RandomAccessibleInterval<T> rastered2d;
        if (hasTpsTransform()) {
            RealRandomAccessible<T> interp2d = Views.interpolate(Views.extendZero(getImg()), new NLinearInterpolatorFactory<>());
            RealRandomAccessible<T> warp2d = RealViews.transform(interp2d, t_tps);
            rastered2d = Views.interval(Views.raster(warp2d), getImg());
        } else {
            rastered2d = getImg();
        }

         // Copy the section into a volume;
        long[] dim3d = Atlas.getVolumeDimension(getImg(), templateResolution, planeOfSection);

        int df = planeOfSection.getFixedAxisIndex();
        long d3 = getSectionNumber();
        long[] lowerBounds = new long[3];
        long[] upperBounds = new long[3];
        for (int d = 0; d < 3; d++) {
            if (d == df) {
                lowerBounds[d] = d3;
                upperBounds[d] = d3;
            } else {
                lowerBounds[d] = 0;
                upperBounds[d] = dim3d[d] - 1;
            }
        }

        Img<T> secVol = getImg().factory().create(dim3d, getImg().firstElement());
        RandomAccessibleInterval<T> sec = Views.interval(secVol, lowerBounds, upperBounds);
        RandomAccess<T> ra = rastered2d.randomAccess();
        Cursor<T> cu = Views.flatIterable(sec).cursor();
        long[] rPos = new long[3];
        while (cu.hasNext()) {
            T value = cu.next();
            cu.localize(rPos);
            long[] sPos = planeOfSection.template2SectionCoordinate(rPos);
            ra.setPosition(sPos);
            value.set(ra.get());
        }

        if (hasSectionTransform() && hasTemplateTransform()) {
            InvertibleRealTransformSequence t = new InvertibleRealTransformSequence();
            t.add(t_s);
            t.add(t_r.inverse());

            RealRandomAccessible<T> interp3d = Views.interpolate(Views.extendZero(secVol), new NLinearInterpolatorFactory<>());
            RealRandomAccessible<T> warp3d = RealViews.transform(interp3d, t);
            RandomAccessibleInterval<T> raster3d = Views.interval(Views.raster(warp3d), templateResolution.getInterval());

            return ImgView.wrap(raster3d, new ArrayImgFactory<>());
        } else {
            return ImgView.wrap(secVol, new ArrayImgFactory<>());
        }
    }

//    public Img<UnsignedShortType> mapTemplate2Section(RandomAccessibleInterval<UnsignedShortType> rai) {
//        return mapTemplate2Section(rai, new NLinearInterpolatorFactory());
//    }

    public Img<UnsignedShortType> mapTemplate2Section(RandomAccessibleInterval<UnsignedShortType> rai, Atlas.Modality modality) {
        InterpolatorFactory interpolator;
        switch (modality) {
            case ANNOTATION:
                interpolator = new NearestNeighborInterpolatorFactory();break;
            case AUTOFLUO:
                interpolator = new NLinearInterpolatorFactory();break;
            case NISSEL:
                interpolator = new NLinearInterpolatorFactory();break;
            default:
                interpolator = new NLinearInterpolatorFactory();break;
        }

        return mapTemplate2Section(rai, interpolator);
    }

    private Img<UnsignedShortType> mapTemplate2Section(RandomAccessibleInterval<UnsignedShortType> rai, InterpolatorFactory interpolator) {
        RandomAccessibleInterval<UnsignedShortType> rastered3d;

        int df = planeOfSection.getFixedAxisIndex();

        if (hasSectionTransform() && hasTemplateTransform() && !t_r.toString().equals(t_s.toString())) {
            long[] upperBound = new long[3];
            int d = 0;
            for (int axis : planeOfSection.getSectionAxesIndices()) {
                upperBound[axis] = getImg().dimension(d++) - 1;
            }
            upperBound[df] = templateResolution.getDimension()[df] - 1;

            InvertibleRealTransformSequence t = new InvertibleRealTransformSequence();
            t.add(t_r);
            t.add(t_s.inverse());

            RealRandomAccessible<UnsignedShortType> interp3d = Views.interpolate(Views.extendZero(rai), interpolator);
            RealRandomAccessible<UnsignedShortType> warp3d = RealViews.transform(interp3d, t);

            rastered3d = Views.interval(Views.raster(warp3d),
                    new long[]{0, 0, 0}, upperBound);
        } else {
            rastered3d = rai;
        }


        RandomAccessibleInterval<UnsignedShortType> section = Views.hyperSlice(rastered3d, df, getSectionNumber());
        if (planeOfSection.swapAxes()) {
//            int fromAxis = planeOfSection.getSectionAxesIndices()[0];
//            int toAxis = planeOfSection.getSectionAxesIndices()[1];
            section = Views.permute(section, 0, 1);
        }

        if (hasTpsTransform()) {
            RealRandomAccessible<UnsignedShortType> interp2d = Views.interpolate(Views.extendZero(section), interpolator);
            RealRandomAccessible<UnsignedShortType> warp2d = RealViews.transform(interp2d, t_tpsi);

            RandomAccessibleInterval<UnsignedShortType> result = Views.interval(Views.raster(warp2d), getImg());
            return ImgView.wrap(result, new ArrayImgFactory<>());
        } else {
            return ImgView.wrap(section, new ArrayImgFactory<>());
        }
    }

    public AffineTransform3D getSectionTransform() {
        return this.t_s;
    }

    public boolean hasSectionTransform() {
        return this.t_s != null;
    }

    public boolean hasTemplateTransform() {
        return this.t_r != null;
    }

    public boolean hasTpsTransform() {
        return this.t_tps != null;
    }
}
