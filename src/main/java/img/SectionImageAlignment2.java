package img;

import bdv.img.TpsTransformWrapper;

import jitk.spline.ThinPlateR2LogRSplineKernelTransform;

import net.imagej.ImageJ;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.Img;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.view.Views;

import java.util.ArrayList;

/**
 * Just a test to work with TPS
 *
 * @author Felix Meyenhofer
 */
public class SectionImageAlignment2 {

    private SectionImageAlignment2() {}

    public static <T extends RealType<T>> void main(String[] args) throws Exception {
        ImageJ ij = new ImageJ();
        ij.ui().showUI();

        //        String secPath = "/Users/turf/switchdrive/SJMCS/data/devel/small-deformations/26836491_25um_red_300_tps1.tif";
//        String secPath = "/Users/turf/switchdrive/SJMCS/data/devel/small-deformations/crym(cy3)_gng2(A488)_IHC(150914)_DGC4_1 - 2016-01-28 05.03.56-FITC_ROI-00.tif";
        String secPath = "/Users/turf/Desktop/new section.tif";
//        String refPath = "/Users/turf/switchdrive/SJMCS/data/devel/small-deformations/average_template_25um_coronal-300.tif";
        String refPath = "/Users/turf/Desktop/reference section.tif";
        int levels = 5;
        int meshRes = 64;

        Img<UnsignedByteType> sec = (Img<UnsignedByteType>) ij.io().open(secPath);
        Img<T> ref = (Img<T>) ij.io().open(refPath);

        RandomAccessibleInterval<BitType> secMsk = SectionImageTool.createMask(sec, ij.op());
        RandomAccessibleInterval<BitType> refMsk = SectionImageTool.createMask(ref, ij.op());

        RandomAccessibleInterval<BitType> secOut = ij.op().morphology().outline(secMsk, false);
        RandomAccessibleInterval<BitType> refOut = ij.op().morphology().outline(refMsk, false);

        SectionImageOutlineSampler secCon = new SectionImageOutlineSampler(secOut, levels);
        secCon.generatePoints();
        SectionImageOutlineSampler refCon = new SectionImageOutlineSampler(refOut, levels);
        refCon.generatePoints();

        // Contour landmark extraction
        secCon.optimize(refCon);
        ArrayList<SectionImageOutlineSampler.OutlinePoint> secPts = secCon.getCorrespondencePoints();
        ArrayList<SectionImageOutlineSampler.OutlinePoint> refPts = refCon.getCorrespondencePoints();

        int N = refPts.size();
        double[][] srcPts = new double[2][N];
        double[][] dstPts = new double[2][N];
        for (int i = 0; i < refPts.size(); i++) {
            double[] srcCoord = secPts.get(i).getCoordinates();
            srcPts[0][i] = srcCoord[0];
            srcPts[1][i] = srcCoord[1];

            double[] dstCoord = refPts.get(i).getCoordinates();
            dstPts[0][i] = dstCoord[0];
            dstPts[1][i] = dstCoord[1];
        }

        ThinPlateR2LogRSplineKernelTransform tps = new ThinPlateR2LogRSplineKernelTransform(2, dstPts, srcPts);
        TpsTransformWrapper transform = new TpsTransformWrapper(2, tps);
        RealRandomAccessible<UnsignedByteType> interp = Views.interpolate(Views.extendZero(sec), new NLinearInterpolatorFactory<>());
        RealRandomAccessible<UnsignedByteType> mapped = RealViews.transform(interp, transform);

        RandomAccessibleInterval<UnsignedByteType> warp = Views.interval(Views.raster(mapped), sec);


        T miSrc = ref.firstElement().createVariable();
        miSrc.setReal(0.0);
        T maSrc = ij.op().stats().max(ref);
        T maSrcT = ref.firstElement().createVariable();
        maSrcT.setReal(255.0);
        RandomAccessibleInterval<UnsignedByteType> nRef = ij.op().convert().uint8(ij.op().image().normalize(ref, miSrc, maSrc, miSrc, maSrcT));

        RandomAccessibleInterval<UnsignedByteType> stk = Views.stack(sec, warp, nRef);
        ij.ui().show(stk);
//        ImageJFunctions.show(stk);


//        // Show the contour without optimization
//        RandomAccessibleInterval<BitType> msk = SectionImageTool.createMask(sec, ij.op());
//        RandomAccessibleInterval<BitType> out = ij.op().morphology().outline(msk, false);
//        SectionImageOutlineSampler sampler = new SectionImageOutlineSampler(out, levels);
//        sampler.generatePoints();
//
//        long[] dim = new long[sec.numDimensions()];
//        sec.dimensions(dim);
//        List<RandomAccessibleInterval<UnsignedByteType>> list = new ArrayList<>();
//        list.addAll(sampler.visualise(dim));
//        list.addAll(alignment.secCon.visualise(dim));
//        list.addAll(alignment.refCon.visualise(dim));
//        ImageJFunctions.show(Views.stack(list), "source, optimized(source), target");
    }
}
