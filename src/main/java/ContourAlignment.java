import ij.CompositeImage;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import img.ij2.SectionImageOutlineSampler;
import img.ij2.SectionImageTool;
import io.scif.img.IO;
import mpicbg.ij.TransformMeshMapping;
import mpicbg.models.*;
import mpicbg.models.Point;
import net.imagej.ImageJ;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.view.Views;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Felix Meyenhofer
 */
public class ContourAlignment {

    private static class Param {
        private boolean interpolate = true;
        private boolean visualize = true;
        private int resolutionOutput = 64;
        private int trinangulationLevels = 4;
    }


    public static void main(String[] args) throws Exception {
        Param p = new Param();


        ImageJ ij = new ImageJ();
        ij.ui().showUI();

//        String secPath = "/Users/turf/switchdrive/SJMCS/data/devel/small-deformations/26836491_25um_red_300_tps1.tif";
        String secPath = "/Users/turf/switchdrive/SJMCS/data/devel/small-deformations/crym(cy3)_gng2(A488)_IHC(150914)_DGC4_1 - 2016-01-28 05.03.56-FITC_ROI-00.tif";
        String refPath = "/Users/turf/switchdrive/SJMCS/data/devel/small-deformations/average_template_25um_coronal-300.tif";

        UnsignedByteType type = new UnsignedByteType();
        ArrayImgFactory<UnsignedByteType> factory = new ArrayImgFactory<>();
        RandomAccessibleInterval<UnsignedByteType> sec = IO.openImgs(secPath, factory, type).get(0);
        RandomAccessibleInterval<UnsignedByteType> ref = IO.openImgs(refPath, factory, type).get(0);

        long[] dim = new long[ref.numDimensions()];
        ref.dimensions(dim);
        int width = (int)dim[0];
        int height = (int)dim[1];

        RandomAccessibleInterval<BitType> secMsk = SectionImageTool.createMask(sec, ij.op());
        RandomAccessibleInterval<BitType> refMsk = SectionImageTool.createMask(ref, ij.op());

        RandomAccessibleInterval<BitType> secOut = ij.op().morphology().outline(secMsk, false);
        RandomAccessibleInterval<BitType> refOut = ij.op().morphology().outline(refMsk, false);

        SectionImageOutlineSampler secCon = new SectionImageOutlineSampler(secOut, p.trinangulationLevels);
        SectionImageOutlineSampler refCon = new SectionImageOutlineSampler(refOut, p.trinangulationLevels);



        List<RandomAccessibleInterval<UnsignedByteType>> outlines = secCon.visualise(dim);
//        ij.ui().show(secVis);
//        CompositeImage imp = new CompositeImage(ImageJFunctions.wrap(secVis, "sec"), CompositeImage.COMPOSITE);
//        imp.show();

        outlines.addAll(refCon.visualise(dim));
//        ij.ui().show(refVis);

        secCon.optimize(refCon);
        outlines.addAll(secCon.visualise(dim));

        ij.ui().show(Views.stack(outlines));

//        RandomAccessibleInterval<UnsignedByteType> vis = Views.stack(sec, secVis, ref, refVis);
        ij.ui().show(Views.stack(secCon.visualise(sec), refCon.visualise(ref)));




        // Alignment
        ArrayList<SectionImageOutlineSampler.OutlinePoint> secPts = secCon.getCorrespondencePoints();
        ArrayList<SectionImageOutlineSampler.OutlinePoint> refPts = refCon.getCorrespondencePoints();

        int nMatches = refPts.size() + 1;
        ArrayList<PointMatch> matches = new ArrayList<>(nMatches);
        for (int i = 0; i < refPts.size(); i++) {
            Point p1 = new Point(secPts.get(i).getCoordinates());
            Point p2 = new Point(refPts.get(i).getCoordinates());
            matches.add(new PointMatch(p1, p2));
        }

        matches.add(new PointMatch(new Point(secCon.getCentroidCoordinates()),
                new Point(refCon.getCentroidCoordinates()), 3));

        final MovingLeastSquaresTransform mlt = new MovingLeastSquaresTransform();
        mlt.setModel(AffineModel2D.class);
        mlt.setAlpha(2.0f);
        mlt.setMatches(matches);

        final CoordinateTransformMesh mltMesh = new CoordinateTransformMesh(mlt, p.resolutionOutput, width, height);
        final TransformMeshMapping<CoordinateTransformMesh> mltMapping = new TransformMeshMapping<CoordinateTransformMesh>(mltMesh);

        final ImagePlus refImp = ImageJFunctions.wrap(ref, "reference");
        final ImagePlus secImp = ImageJFunctions.wrap(sec, "section");

        final ImageProcessor source, target;
//        if (p.rgbWithGreenBackground) {
//            target = new ColorProcessor(width, height);
//            for (int j = width * height - 1; j >= 0; --j)
//                target.set(j, 0xff00ff00);
//            source = stack.getProcessor(slice).convertToRGB();
//        } else {
        source = secImp.getProcessor();
        target = source.createProcessor(width, height);
//        }

        if (p.interpolate) {
            mltMapping.mapInterpolated(source, target);
        } else {
            mltMapping.map(source, target);
        }
        final ImagePlus wrap = new ImagePlus("elastic mlt ", target);
        if (p.visualize) {
            final Shape shape = mltMesh.illustrateMesh();
            wrap.setOverlay(shape, new Color(0x00FF00), new BasicStroke(1));
        }

        wrap.show();
        ImageStack stk = new ImageStack(width, height, 3);
        stk.setProcessor(secImp.getProcessor(), 1);
        stk.setProcessor(wrap.getProcessor(), 2);
        stk.setProcessor(refImp.getProcessor(), 3);

        CompositeImage res = new CompositeImage(new ImagePlus("result", stk), CompositeImage.COMPOSITE);
        res.show();



//            IJ.save(impTarget, p.outputPath + "elastic-" + String.format("%05d", i) + ".tif");
//        }





////        Converter<UnsignedByteType, ARGBType> rc = new ChannelARGBConverter(ChannelARGBConverter.Channel.R);
////        RandomAccessibleInterval<ARGBType> red = Converters.convert(sec, rc, new ARGBType());
//        RandomAccessibleInterval<ARGBType> red = new ArrayImgFactory<ARGBType>().create(sec, new ARGBType());
//        secCon.visualize(secPts, 0xFFFF00, red);
//        ImageJFunctions.show(red);
//
////        Converter<UnsignedByteType, ARGBType> gc = new ChannelARGBConverter(ChannelARGBConverter.Channel.G);
////        RandomAccessibleInterval<ARGBType> green = Converters.convert(ref, gc, new ARGBType());
//        RandomAccessibleInterval<ARGBType> green = new ArrayImgFactory<ARGBType>().create(sec, new ARGBType());
//        refCon.visualize(refPts, 0xFF00FF, green);
//        ImageJFunctions.show(green);







//        RandomAccessibleInterval<ARGBType> pts = new ArrayImgFactory<ARGBType>().create(sec, new ARGBType());
//        RandomAccess<ARGBType> ra = pts.randomAccess();
//
//        for (int p = 0; p < secPts.length; p ++) {
//            ra.setPosition(secPts[p]);
//            if (p == 0) {
//                HyperSphere<ARGBType> sphere = new HyperSphere<ARGBType>(pts, ra, 3);
//                for (ARGBType pixel : sphere) {
//                    pixel.set(new ARGBType(0xFFFF00));
//                }
//            } else {
//                ra.get().set(new ARGBType(0xFFFF00));
//            }
//
//            ra.setPosition(refPts[p]);
//            if (p == 0) {
//                HyperSphere<ARGBType> sphere = new HyperSphere<>(pts, ra, 3);
//
//                for (ARGBType pixel : sphere) {
//                    pixel.set(new ARGBType(0x00FFFF));
//                }
//            } else {
//                ra.get().set(new ARGBType(0x00FFFF));
//            }
//        }
//
//        Converter<UnsignedByteType, ARGBType> gc = new ChannelARGBConverter(ChannelARGBConverter.Channel.G);
//        RandomAccessibleInterval<ARGBType> green = Converters.convert(ref, gc, new ARGBType());
//        Converter<UnsignedByteType, ARGBType> rc = new ChannelARGBConverter(ChannelARGBConverter.Channel.R);
//        RandomAccessibleInterval<ARGBType> red = Converters.convert(sec, rc, new ARGBType());
//
//        RandomAccessibleInterval<ARGBType> view = Views.stack(red, green, pts);
//
//        ImageJFunctions.show(view);
    }
}
