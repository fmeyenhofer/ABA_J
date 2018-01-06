import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Plot;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import mpicbg.ij.InverseTransformMapping;
import mpicbg.ij.Mapping;
import mpicbg.ij.SIFT;
import mpicbg.imagefeatures.*;
import mpicbg.models.*;
import net.imagej.ImageJ;
import net.imagej.Dataset;
import net.imagej.ops.OpService;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.logic.BitType;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.io.IOService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import img.ImagePlusUtils;
import img.SectionImageTool;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * TODO: iteratively run over different resolutions
 * TODO: use the pixel size for scaling the input section to the template (instead of the mask area)
 *
 * @author Felix Meyenhofer
 */
@Plugin(type = Command.class, menuPath = "Plugins > Allen Brain Atlas > Mapping > Automatic")
public class SectionToVolumeRegistration implements Command {

    @Parameter
    private LogService log;

    @Parameter
    private OpService op;

    @Parameter
    private UIService ui;


    @Parameter(label = "Section image")
    private Dataset section;

    @Parameter(label = "Reference volume")
    private Dataset volume;

//    @Parameter(label = "Modality", choices = {"auto-fluorescence", "nissl"})
//    private String modality;
//
//    @Parameter(label = "Resolution", choices = {"10um, 25um, 50um"})
//    private String resolution;
//
//    @Parameter(label = "perspective", choices = {"coronal", "sagital", "horizontal"})
//    private String perspective;


    // Parameter (that might get integrated in the configuration dialog)
    private final double saturationFraction = 0.99;

    private final double scaleTolerance = 0.05;


    static private class Param {
        final FloatArray2DSIFT.Param sift = new FloatArray2DSIFT.Param();
        float rod = 0.92f;
        float maxEpsilon = 25.0f;
        float minInlierRatio = 0.05f;
        int modelIndex = 1;
        boolean interpolate = true;
        boolean showInfo = false;
    }


    private final static Param p = new Param();



    final private List<Feature> fsSli = new ArrayList<>();
    final private List<Feature> fsVol = new ArrayList<>();


    @Override
    public void run() {
        IJ.run("Console", "uiservice=[org.scijava.ui.DefaultUIService [priority = 0.0]]");
        log.info("Section to volume alignment...");

        // Get the input data
        final ImagePlus ref = ImageJFunctions.wrap((RandomAccessibleInterval) volume.getImgPlus().getImg(), "Volume");

        final Img sliRai = section.getImgPlus().getImg();
        final ImagePlus oriSec = ImageJFunctions.wrap(sliRai, "Section");

        // Adjust the contrast
        log.info(" adjust contrast");
        ImagePlusUtils.adjustContrast(ref.getProcessor(), saturationFraction);
        ImagePlusUtils.adjustContrast(oriSec.getProcessor(), saturationFraction);

        // Get the reference volume
//        File path = cache.getReferenceVolume(modality, resolution);
//        ImagePlus ref = new ImagePlus();
//        IJ.run(ref,"Nrrd ...", path.getAbsolutePath());
//        ImagePlus ref = IJ.openImage(path.getAbsolutePath());

        // Initialize
        ImageProcessor secPro = oriSec.duplicate().getStack().getProcessor(1);
        secPro.setBackgroundValue(0);
        ImageStack refStack = ref.getStack();
        ImageProcessor refPro;

        final int w = ref.getWidth();
        final int h = ref.getHeight();
        final int d = refStack.getSize();

        double currentSliLength = 0;
        double scale = 1;

        final FloatArray2DSIFT sift = new FloatArray2DSIFT(p.sift);
        final SIFT ijSIFT = new SIFT(sift);

        List<AbstractAffineModel2D<?>> models = new ArrayList<>(d);
        List<Vector<PointMatch>> sectionCandidates = new ArrayList<>();
        List<Vector<PointMatch>> sectionInliers = new ArrayList<>();

        float[] scales = new float[d];
        float[] cans = new float[d];
        float[] msds = new float[d];
        float[] nfea = new float[d];
        float[] inls = new float[d];
        float[] csts = new float[d];
        float[] score = new float[d];
        boolean[] status = new boolean[d];
        float[] x = new float[d];

        // Threshold the section and get the surface area
        log.info(" create mask of target section");
        RandomAccessibleInterval<BitType> sliMsk = SectionImageTool.createMask(sliRai, op);
        int sliArea = SectionImageTool.getMaskArea(sliMsk);
        double sliLength = Math.sqrt(sliArea);
        ui.show( "target section mask (area=" + sliArea + ")", sliMsk);

        // Extract SIFT for each section in the reference and try to find a mapping
        for (int s = 1; s <= d; ++s) {
            fsVol.clear();

            int i = s - 1;

            ref.setSlice(s);
            ref.updateAndRepaintWindow();

            // get the reference mask area
            refPro = refStack.getProcessor(s);
            ImagePlus refSliImp = new ImagePlus();
            refSliImp.setProcessor(refPro);


            Img img = ImageJFunctions.wrap(refSliImp);
            RandomAccessibleInterval refMsk = SectionImageTool.createMask(img, op);
            int refArea = SectionImageTool.getMaskArea(refMsk);
//            int refArea = ImageProcessingTools.getMaskArea(refPro);
            double refLength = Math.sqrt((double) refArea);

            // scale the section image to fit the current reference section
            double scaleDiff = Math.abs(currentSliLength - refLength) / refLength;
            if (scaleDiff > scaleTolerance) {
                scale = refLength / sliLength;
                currentSliLength = refLength;
                log.info( " scale target section: " + scale);
                secPro = oriSec.duplicate().getStack().getProcessor(1);
                secPro.setBackgroundValue(0);
                secPro.scale(scale, scale);
                fsSli.clear();
            }

            // extract SIFT on the target section
            if (fsSli.isEmpty()) {
//                ImagePlus tmp = new ImagePlus("sec pro", secPro);
//                tmp.show();
                log.info( " extract SIFT from target section:" );
                ijSIFT.extractFeatures(secPro, fsSli);
                log.info("    " + fsSli.size() + " features extracted.");
            }


            log.info( " extract SIFT from reference volume, section " + s + ":" );
            ijSIFT.extractFeatures(refPro, fsVol);
            log.info( "    " + fsVol.size() + " features extracted." );

            final Vector< PointMatch > candidates =
                    FloatArray2DSIFT.createMatches(fsSli, fsVol, 1.5f, null, Float.MAX_VALUE, p.rod );
            log.info("    correspondences (brute force): " + candidates.size());

            log.info( "    RANSAC:" );
            final Vector<PointMatch> inliers = new Vector<>();
            AbstractAffineModel2D model = new RigidModel2D();

            boolean modelFound;
            try {
                modelFound = model.filterRansac(
                        candidates,
                        inliers,
                        1000,
                        p.maxEpsilon,
                        p.minInlierRatio);
            } catch (final Exception e) {
                modelFound = false;
                log.error("    " + e.getMessage());
                log.error("    No mapping was determined.");
            }

            log.info("     cost:    " + model.getCost());
            log.info("     inliers: " + inliers.size());

            log.info("    transform target section");
            final ImageProcessor alignedSlice = mapSection(secPro, model, w, h, p.interpolate);

            log.info("    compute difference between reference slice and aligned slice...");
            float msd = ImagePlusUtils.meanSquareDifference(alignedSlice, refPro);
            log.info("    msd:     " + msd);


            // Collect section information
            scales[i] = (float) scale;
            models.add((AbstractAffineModel2D) model.copy());
            sectionCandidates.add((Vector<PointMatch>) candidates.clone());
            sectionInliers.add((Vector<PointMatch>) inliers.clone());
            x[i] = (float) s;
            nfea[i] = fsVol.size();
            cans[i] = candidates.size();
            inls[i] = inliers.size();
            csts[i] = (float) model.getCost();
            msds[i] = msd;
            status[i] = modelFound;
            score[i] = csts[i] / inls[i];
        }

        Plot plot1 = new Plot("MSD to ref. section", "section number", "MSD", x, msds);
        plot1.show();
        Plot plot2 = new Plot("features", "section number", "number of features", x, nfea);
        plot2.show();
        Plot plot3 = new Plot("correspondences (brute force)", "section number", "number of matches", x, cans);
        plot3.show();
        Plot plot4 = new Plot("correspondences (RANSAC)", "section number", "number of inliers", x, inls);
        plot4.show();
        Plot plot5 = new Plot("costs", "section number", "-", x, csts);
        plot5.show();
        Plot plot6 = new Plot("score", "section number", "-", x, score);
        plot6.show();
        Plot plot7 = new Plot("scale", "section number", "-", x, scales);
        plot7.show();

        log.info( "...Done!" );


        // Get the index of the lowest score
        int index = 0;

//        float max = 0;
//        for (int i = 1; i < inls.length; i++) {
//            if (max < inls[i]) {
//                max = inls[i];
//                index = i;
//            }
//        }

        float cn = Float.MAX_VALUE;
        for (int i = 0; i < score.length; i++) {
            if (cn > score[i]) {
                cn = score[i];
                index = i;
            }
        }

        secPro = oriSec.duplicate().getStack().getProcessor(1);
        secPro.setBackgroundValue(0);
        secPro.scale(scales[index], scales[index]);
        ui.show("before mapping", new ImagePlus("section", secPro));
        ImageProcessor mapped = mapSection(secPro, models.get(index), w, h, p.interpolate);
        ui.show("after mapping", new ImagePlus("mapped", mapped));

        ImageStack stk = new ImageStack(w, h);
        stk.addSlice("ref. section " + index, refStack.getProcessor(index + 1));
        stk.addSlice("src. section", mapped);

        ImagePlus overlay = new ImagePlus("Overlay with section " + (index + 1), stk);
        overlay.setDimensions(2, 1, 1);
        CompositeImage composite = new CompositeImage(overlay);
        composite.setMode(CompositeImage.COMPOSITE);
        composite.show();

        showMatches(secPro, refStack.getProcessor(index + 1), sectionCandidates.get(index), sectionInliers.get(index), 1);
    }

    private ImageProcessor mapSection(ImageProcessor section, AbstractAffineModel2D model,
                                      int width, int height, boolean interpolate) {
        final Mapping mapping = new InverseTransformMapping<AbstractAffineModel2D<?>>(model);

        ImageProcessor src = section.duplicate();
        src.setInterpolationMethod(ImageProcessor.BILINEAR);
        final ImageProcessor dst = src.createProcessor(width, height);
//        alignedSlice.setMinAndMax(lb, ub);

        if (interpolate) {
            mapping.mapInterpolated(src, dst);
        } else {
            mapping.map(src, dst);
        }

        return dst;
    }


    /**
     * downscale a grey scale float image using gaussian blur
     */
    private static ImageProcessor downScale(final ImageProcessor ip, final double s) {
        final FloatArray2D g = new FloatArray2D(ip.getWidth(), ip.getHeight());
        ImageArrayConverter.imageProcessorToFloatArray2D(ip, g);

        final float sigma = (float) Math.sqrt(0.25 * 0.25 / s / s - 0.25);
        final float[] kernel = Filter.createGaussianKernel(sigma, true);

        final FloatArray2D h = Filter.convolveSeparable(g, kernel, kernel);

        final FloatProcessor fp = new FloatProcessor(ip.getWidth(), ip.getHeight());

        ImageArrayConverter.floatArray2DToFloatProcessor(h, fp);
        return ip.resize((int) (s * ip.getWidth()));
    }


    private static void showMatches(ImageProcessor target,
                                    ImageProcessor reference,
                                    Vector<PointMatch> candidates,
                                    Vector<PointMatch> inliers,
                                    double vis_scale) {
        ImageProcessor targetPro = downScale(target.convertToRGB().duplicate(), vis_scale);
        ImageProcessor referencePro = downScale(reference.convertToRGB().duplicate(), vis_scale);
        targetPro.setColor(Color.red);
        referencePro.setColor(Color.red);

        targetPro.setLineWidth(2);
        referencePro.setLineWidth(2);
        drawPoints(vis_scale, targetPro, referencePro, candidates);

        targetPro.setColor(Color.green);
        referencePro.setColor(Color.green);
        targetPro.setLineWidth(2);
        referencePro.setLineWidth(2);
        drawPoints(vis_scale, targetPro, referencePro, inliers);

//        ImageStack stackInfo = new ImageStack((int) Math.round(vis_scale * reference.getWidth()),
//                (int) Math.round(vis_scale * reference.getHeight()));
        
        ImageProcessor tmp;
        tmp = targetPro.createProcessor(targetPro.getWidth(), targetPro.getHeight());
        tmp.insert(targetPro, 0, 0);
//        stackInfo.addSlice(null, tmp); // fixing silly 1 pixel size missmatches

        ImagePlus imp1 = new ImagePlus("target section matches", tmp);
        imp1.show();

        tmp = referencePro.createProcessor(referencePro.getWidth(), referencePro.getHeight());
        tmp.insert(referencePro, 0, 0);
//        stackInfo.addSlice(null, tmp);

        ImagePlus imp2 = new ImagePlus("reference section matches", tmp);
        imp2.show();

//        ImagePlus impInfo = impInfo = new ImagePlus("Alignment info", stackInfo);
//        impInfo.setStack("Alignment info", stackInfo);
//        final int currentSlice = impInfo.getSlice();
//        impInfo.setSlice(stackInfo.getSize());
//        impInfo.setSlice(currentSlice);
//
//        impInfo.show();
//        impInfo.updateAndDraw();
    }


    private static void drawPoints(double vis_scale,
                                   ImageProcessor ip3,
                                   ImageProcessor ip4,
                                   Vector<PointMatch> candidates) {
        for ( final PointMatch m : candidates ) {
            final double[] m_p1 = m.getP1().getL();
            final double[] m_p2 = m.getP2().getL();

            ip3.drawDot((int) Math.round(vis_scale * m_p1[0]), (int) Math.round(vis_scale * m_p1[1]));
            ip4.drawDot((int) Math.round(vis_scale * m_p2[0]), (int) Math.round(vis_scale * m_p2[1]));
        }
    }


    public static void main(String[] args) throws IOException {
//        Debug.run("Map Section to Volume", "");
        
        final ImageJ ij = new net.imagej.ImageJ();
        ij.ui().showUI();

//        Object vol = ij.io().open("/Users/turf/switchdrive/SJMCS/data/devel/section2volume/average_template_25um_coronal_295-315.tif");
        Object vol = ij.io().open("/Users/turf/switchdrive/SJMCS/data/devel/section2volume/average_template_25um_coronal_290-295.tif");
//        Object vol = ij.io().open("/Users/turf/switchdrive/SJMCS/data/devel/section2volume/average_template_50um_coronal_120-160.tif");
        ij.ui().show(vol);

        Object sec = ij.io().open("/Users/turf/switchdrive/SJMCS/data/devel/section2volume/26836491_25um_red_300_rot13-tps.tif");
//        Object sec = ij.io().open("/Users/turf/switchdrive/SJMCS/data/devel/section2volume/crym(cy3)_gng2(A488)_IHC(150914)_DGC4_1 - 2016-01-28 05.03.56-FITC_ROI-00.tif");
        ij.ui().show(sec);
        
        ij.command().run(SectionToVolumeRegistration.class, true);


//        AllenCache cache = new AllenCache();
//        File path = cache.getReferenceVolume("auto-fluorescence", "100um");

//        Object obj = ij.io().open(path.getAbsolutePath());
//        ij.ui().show(obj);

//        ImagePlus ref = new ImagePlus();
//        IJ.run(ref, "Nrrd ...", path.getAbsolutePath());
//        ImagePlus ref =IJ.openImage(path.getAbsolutePath());
//        ref.show();
    }
}
