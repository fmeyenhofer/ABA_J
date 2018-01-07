package img;

import ij.CompositeImage;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import io.scif.img.IO;
import mpicbg.ij.TransformMeshMapping;
import mpicbg.models.*;
import mpicbg.models.Point;
import net.imagej.ImageJ;
import net.imagej.ops.OpService;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.view.Views;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Felix Meyenhofer
 */
public class SectionImageAlignment <T extends RealType<T> & NativeType<T>>  {

    private boolean interpolate = true;
    private int resolutionOutput = 64;
    private int triangulationLevels = 4;

    private SectionImageOutlineSampler secCon;
    private SectionImageOutlineSampler refCon;
    private OpService ops;
    private final Img<T> source;
    private final Img<T> target;
    private CoordinateTransformMesh mltMesh;
    private ImagePlus warp;


    public SectionImageAlignment(Img<T> srcImg, Img<T> dstImg,
                                 int meshResolution, boolean interpolate, int outlineSamplingLevels,
                                 OpService opService) {
        this.source = srcImg;
        this.target = dstImg;
        this.ops = opService;

        this.resolutionOutput =  meshResolution;
        this.triangulationLevels = outlineSamplingLevels;
        this.interpolate = interpolate;
    }


    public Img map() throws Exception {
        long[] dim = new long[source.numDimensions()];
        source.dimensions(dim);
        int width = (int) dim[0];
        int height = (int) dim[1];

        RandomAccessibleInterval<BitType> secMsk = SectionImageTool.createMask(source, ops);
        RandomAccessibleInterval<BitType> refMsk = SectionImageTool.createMask(target, ops);

        RandomAccessibleInterval<BitType> secOut = ops.morphology().outline(secMsk, false);
        RandomAccessibleInterval<BitType> refOut = ops.morphology().outline(refMsk, false);

        secCon = new SectionImageOutlineSampler(secOut, triangulationLevels);
        secCon.generatePoints();
        refCon = new SectionImageOutlineSampler(refOut, triangulationLevels);
        refCon.generatePoints();

        // Alignment
        secCon.optimize(refCon);
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

        mltMesh = new CoordinateTransformMesh(mlt, resolutionOutput, width, height);
        final TransformMeshMapping<CoordinateTransformMesh> mltMapping = new TransformMeshMapping<>(mltMesh);

        // TODO: ij2 this!
        final ImagePlus secImp = ImageJFunctions.wrap(source, "section");
        final ImageProcessor srcIp, trgIp;
        srcIp = secImp.getProcessor();
        trgIp = srcIp.createProcessor(width, height);

        if (interpolate) {
            mltMapping.mapInterpolated(srcIp, trgIp);
        } else {
            mltMapping.map(srcIp, trgIp);
        }

        warp = new ImagePlus("mapped section", trgIp);

        return ImageJFunctions.wrap(warp);
    }

    public void visualise() {
        long[] dim = new long[source.numDimensions()];
        source.dimensions(dim);

        // Outlines (including optimization step)
//        List<RandomAccessibleInterval<UnsignedByteType>> outlines = secCon.visualise(dim);
//        outlines.addAll(refCon.visualise(dim));
//        ImageJFunctions.show(Views.stack(outlines), "outlines");

        // Overlay of contours and sections
        ImageJFunctions.show(
                Views.stack(secCon.visualise(ops.convert().uint8(source)),
                            refCon.visualise(ops.convert().uint8(target))), "outlines");

        // Overlay of source target and warp
        final ImagePlus secImp = ImageJFunctions.wrap(source, "source");
        final ImagePlus refImp = ImageJFunctions.wrap(target, "target");
        final Shape shape = mltMesh.illustrateMesh();

        ImageStack stk = new ImageStack(secImp.getWidth(), secImp.getHeight(), 3);
        stk.setProcessor(secImp.getProcessor(), 1);
        stk.setProcessor(warp.getProcessor(), 2);
        stk.setProcessor(refImp.getProcessor(), 3);

        CompositeImage res = new CompositeImage(new ImagePlus("warp", stk), CompositeImage.COMPOSITE);
        res.show();

        // Visualize deformation
        warp.setOverlay(shape, new Color(0x00FF00), new BasicStroke(1));
        warp.show();
    }


    public static void main(String[] args) throws Exception {
        //        String secPath = "/Users/turf/switchdrive/SJMCS/data/devel/small-deformations/26836491_25um_red_300_tps1.tif";
        String secPath = "/Users/turf/switchdrive/SJMCS/data/devel/small-deformations/crym(cy3)_gng2(A488)_IHC(150914)_DGC4_1 - 2016-01-28 05.03.56-FITC_ROI-00.tif";
        String refPath = "/Users/turf/switchdrive/SJMCS/data/devel/small-deformations/average_template_25um_coronal-300.tif";
        int levels = 5;

        ImageJ ij = new ImageJ();
        ij.ui().showUI();

        UnsignedByteType type = new UnsignedByteType();
        ArrayImgFactory<UnsignedByteType> factory = new ArrayImgFactory<>();
        Img<UnsignedByteType> sec = IO.openImgs(secPath, factory, type).get(0);
        Img<UnsignedByteType> ref = IO.openImgs(refPath, factory, type).get(0);

        SectionImageAlignment alignment = new SectionImageAlignment(sec, ref,
                64, true, levels,  ij.op());
        alignment.map();
        alignment.visualise();

        // Show the contour without optimization
        RandomAccessibleInterval<BitType> msk = SectionImageTool.createMask(sec, ij.op());
        RandomAccessibleInterval<BitType> out = ij.op().morphology().outline(msk, false);
        SectionImageOutlineSampler sampler = new SectionImageOutlineSampler(out, levels);
        sampler.generatePoints();

        long[] dim = new long[sec.numDimensions()];
        sec.dimensions(dim);
        List<RandomAccessibleInterval<UnsignedByteType>> list = new ArrayList<>();
        list.addAll(sampler.visualise(dim));
        list.addAll(alignment.secCon.visualise(dim));
        list.addAll(alignment.refCon.visualise(dim));
        ImageJFunctions.show(Views.stack(list), "source, optimized(source), target");
    }
}
