package gui.bdv;

import img.AraImgPlus;
import img.SectionImageOutline;
import img.SectionImageTool;
import img.VolumeSection;
import net.imglib2.algorithm.morphology.Closing;
import net.imglib2.algorithm.morphology.StructuringElements;
import net.imglib2.algorithm.neighborhood.Shape;
import rest.AllenRefVol;
import rest.Atlas;

import bdv.util.Bdv;
import bdv.util.BdvSource;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.util.BdvHandlePanel;
import bdv.util.AxisOrder;
import bdv.util.BdvFunctions;
import bdv.BigDataViewer;
import bdv.img.TpsTransformWrapper;
import bdv.tools.HelpDialog;
import bdv.tools.ToggleDialogAction;
import bdv.viewer.ViewerOptions;
import bdv.viewer.state.SourceState;
import bdv.viewer.state.ViewerState;
import bdv.viewer.VisibilityAndGrouping;

import mpicbg.spim.data.SpimDataException;

import org.scijava.app.StatusService;
import org.scijava.ui.behaviour.KeyStrokeAdder;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.AbstractNamedAction;

import net.imagej.ops.OpService;
import net.imglib2.type.logic.BitType;
import net.imglib2.Dimensions;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.Img;
import net.imglib2.img.ImgView;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.*;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.view.Views;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.NativeType;

import jitk.spline.ThinPlateR2LogRSplineKernelTransform;

import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;

import javax.swing.JFrame;
import javax.swing.InputMap;
import javax.swing.ActionMap;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.ArrayUtils;


/**
 * TODO: Add possibility to work with different perspectives: coronal works, sagital and horizontal need to be added [1]
 * TODO: Make it so that the section volume AND the template volume can be transformed. Currently transforming the section volume results in wrong TPS wraps
 * TODO: shortcuts F1, F10, F11 and F12 do not work (BDV functionality for help, movie recording, and load/save settings). Added workaround/override for F1
 * TODO: The BDV threads are not terminated properly there are 'Fetcher-0' threads remaining after closing the window.
 * TODO: with the Outlier removal, when re-warping, the bdv series get messed up
 *
 * @author Felix Meyenhofer
 */
public class InteractiveAlignmentUi<V extends RealType<V> & NativeType<V>> {

    private final StatusService status;
    private final OpService ops;


    private static int DISPLAY_WIDTH = 652;
    private static int DISPLAY_HEIGHT = 512;
    private static int SECTION_INDEX = 0;
    private static int[] SECTION_OUTLIINE_INDICES = new int[]{1, 2, 7};
    private static int SECTION_WARP_INDEX = 6;
    private static int TEMPLATE_INDEX = 3;
    private static int[] TEMPLATE_OUTLINE_INDICES = new int[]{4, 5};

    private boolean debug = false;

    //    private boolean interpolate = true;
//    private int resolutionOutput = 64;
    private int triangulationLevels;
    private boolean optimizeCorrespondences;
    private boolean removeOutliers;


    private BdvHandlePanel bdvHandle;
    private static BdvSource sectionSource = null;
    private static BdvSource warpedSectionSource = null;
    private static BdvSource sectionOutlierSrc = null;
    private static BdvSource templateOutlineSrc1 = null;
    private static BdvSource templateOutlineSrc2 = null;

    private AraImgPlus<V> secImg;
    private double secMaxInt;
    private RandomAccessibleInterval secVol;
    private AllenRefVol refVol;
    private Dimensions dims;
    private AffineTransform3D Tr_init;
    private AffineTransform3D Ts_init;
    private VolumeSection volumeSection;

    private SectionImageOutline secCon;
    private TpsTransformWrapper t_tps;
    private TpsTransformWrapper t_tpsi;


    public InteractiveAlignmentUi(AraImgPlus<V> secImg, AllenRefVol refVolFile,
                                  int levels, boolean optimizeCorrespondences, boolean removeOutliers,
                                  OpService opService, StatusService statusService)
            throws SpimDataException {
        this.ops = opService;
        this.status = statusService;
        this.secImg = secImg;
        this.refVol = refVolFile;
        this.triangulationLevels = levels;
        this.optimizeCorrespondences = optimizeCorrespondences;
        this.removeOutliers = removeOutliers;
        this.dims = refVol.getDimensions();
        this.Tr_init = refVol.getTransform();

        secVol = secImg.createSectionVolume(refVol);

        V max = ops.stats().max(secImg);
        secMaxInt = max.getRealDouble() * 0.66; // TODO: better auto-contrast method

        // TODO: this might be better in the plugin code instead of here.
        // Extract the outline points of the input section (once!)
        status.showStatus(0, 100, "create section mask");
        RandomAccessibleInterval<BitType> secMsk = SectionImageTool.createMask(secImg, ops);
        int mskArea = SectionImageTool.getMaskArea(secMsk);

        long seRad = Math.round(Math.sqrt(((double) mskArea * 0.00075) / Math.PI));//TODO: Parameter (expose?)
        status.showStatus(30, 100, "morph. closing diamond " + seRad);
        List<Shape> strel = StructuringElements.diamond((int) seRad, 1);
        Img<BitType> secMskMorph = ops.create().img(secMsk);
        Closing.close(Views.extendZero(secMsk), secMskMorph, strel, 1);
        Img<BitType> secMskHol = ops.create().img(secMskMorph);
        ops.morphology().fillHoles(secMskHol, secMskMorph);
//        ImageJFunctions.show(secMskHol);

        status.showStatus(60, 100, "create section outline points");
        RandomAccessibleInterval<BitType> secOut = ops.morphology().outline(secMskHol, false);
        secCon = new SectionImageOutline(secOut, triangulationLevels);
        secCon.sample();
        status.showStatus(100, 100, "done loading section image");
    }

    private void cleanup() {
        bdvHandle = null;
        sectionSource = null;
        warpedSectionSource = null;
        sectionOutlierSrc = null;
        templateOutlineSrc1 = null;
        templateOutlineSrc2 = null;
        secImg = null;
        secVol = null;
        refVol = null;
        dims = null;
        Tr_init = null;
        Ts_init = null;
        volumeSection = null;
        secCon = null;
        t_tps = null;
        t_tpsi = null;
        System.gc();
    }

    private void updateInputSection() {
        AffineTransform3D tr = getSourceTransformation(TEMPLATE_INDEX);
        AffineTransform3D ts = getSourceTransformation(SECTION_INDEX);

        secImg.updateRegistrationInfo(t_tps, t_tpsi, ts, tr, volumeSection);
    }

    public void createAndShow() throws SpimDataException {
        JFrame window = new JFrame("Interactive Section Alignment");
        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                updateInputSection();

                bdvHandle.getViewerPanel().stop();
                window.dispose();
                cleanup();
            }
        });

        // General options
        BdvOptions options = Bdv.options();
        options.axisOrder(AxisOrder.XYZ);
        bdvHandle = new BdvHandlePanel(window, options);

        // Make sure only the reference volume is transformed.
//        bdvHandle.getViewerPanel().addTransformListener(new TransformListener<AffineTransform3D>() {
//            @Override
//            public void transformChanged(AffineTransform3D transform3D) {
//                ViewerState state = bdvHandle.getViewerPanel().getState();
//                if (state.getCurrentSource() != TEMPLATE_INDEX) {
//                    bdvHandle.getViewerPanel().showMessage("transforms only on template!");
//                    state.setCurrentSource(TEMPLATE_INDEX);
//                }
//            }
//        });

        // Configure the UI window
        window.add(bdvHandle.getViewerPanel(), BorderLayout.CENTER);
        window.setBounds(50, 50, DISPLAY_WIDTH, DISPLAY_HEIGHT);
        window.setVisible(true);

        // Add key binding
        InputTriggerConfig triggerConfig = BigDataViewer.getInputTriggerConfig(ViewerOptions.options());
        bdvHandle.getKeybindings().addInputMap("bdv ia", createInputMap(triggerConfig));
        HelpDialog dialog = new HelpDialog(window,
                InteractiveAlignmentUi.class.getResource("/bdv/Help.html"));
        bdvHandle.getKeybindings().addActionMap("bdv ia", createActionMap(dialog));

        // Add the section volume
        if (secImg.hasSectionTransform()) {
            Ts_init = secImg.getSectionTransform();
        } else {
            Ts_init = this.Tr_init;
        }

        sectionSource = BdvFunctions.show(secVol,
                "input section",
                Bdv.options().addTo(bdvHandle).sourceTransform(Ts_init));
        sectionSource.setColor(new ARGBType(0x6f6f6f));
        sectionSource.setActive(true);
        sectionSource.setDisplayRange(0, secMaxInt);

        // Add section contour points
        double sectionNumber = dims.dimension(0) / 2;
        Atlas.PlaneOfSection plane = secImg.getPlaneOfSection();
        ArrayList<SectionImageOutline.OutlinePoint> secPts = secCon.getSamples();

        BdvSource sectionOutlineSrc1 = BdvFunctions.showOverlay(
                new SectionImageOutlinePoints(secPts, sectionNumber, plane,
                        false, SectionImageOutlinePoints.PointMarker.OVAL),
                "section outline points",
                Bdv.options().addTo(bdvHandle).sourceTransform(Ts_init));
        sectionOutlineSrc1.setColor(new ARGBType(0x0000FF));
        sectionOutlineSrc1.setDisplayRangeBounds(0, 1);
        sectionOutlineSrc1.setActive(false);

        BdvSource sectionOutlineSrc2 = BdvFunctions.showOverlay(
                new SectionImageOutlinePoints(secCon.getCentroidCoordinates(), secPts.get(0).getCoordinates(),
                        sectionNumber, plane, false, SectionImageOutlinePoints.PointMarker.OVAL),
                "section centroid and 1st",
                Bdv.options().addTo(bdvHandle).sourceTransform(Ts_init));
        sectionOutlineSrc2.setColor(new ARGBType(0xFF00FF));
        sectionOutlineSrc2.setDisplayRangeBounds(1, 1);
        sectionOutlineSrc2.setActive(false);

        // Add the reference volume
        List<BdvStackSource<?>> stkSources = BdvFunctions.show(refVol.getHdf5(),
                Bdv.options().addTo(bdvHandle));
        stkSources.get(0).setColor(new ARGBType(0x00FF00));
        stkSources.get(0).setActive(true);
        stkSources.get(0).setCurrent();

        // Get the current view transform and rotate it to the yz-plane
        AffineTransform3D tvw = new AffineTransform3D();
        bdvHandle.getViewerPanel().getState().getViewerTransform(tvw);
        int rotationAxisIndex = plane.getRotationAxis(Atlas.PlaneOfSection.SAGITAL);
        tvw.rotate(rotationAxisIndex, Math.PI / 2);

        // Assemble the entire transform from source to display
        InvertibleRealTransformSequence tlv = new InvertibleRealTransformSequence();
        tlv.add(Tr_init);
        tlv.add(tvw);

        // Check where the center of the reference volume is and put it in the middle of the display
        double[] cen = new double[]{(double) (dims.dimension(0) / 2),
                (double) (dims.dimension(1) / 2),
                (double) (dims.dimension(2) / 2)};
        double[] cen_t = new double[3];
        tlv.apply(cen, cen_t);
        double[] dcen = new double[]{DISPLAY_WIDTH / 2 - cen_t[0], DISPLAY_HEIGHT / 2 - cen_t[1], -cen_t[2]};
        tvw.translate(dcen);
        bdvHandle.getViewerPanel().setCurrentViewerTransform(tvw);
    }

    private InputMap createInputMap(final KeyStrokeAdder.Factory keyProperties) {
        final InputMap inputMap = new InputMap();
        final KeyStrokeAdder map = keyProperties.keyStrokeAdder(inputMap);

        map.put("warp section", "W");
        map.put("toggle warp", "shift W");
        map.put("toggle points", "shift P");
        map.put("update section", "shift U");
        map.put("section difference", "shift D");
        map.put("help", "F1", "H");

        return inputMap;
    }

    private ActionMap createActionMap(HelpDialog dialog) {
        final ActionMap actionMap = new ActionMap();

        int[] sections = new int[]{SECTION_INDEX, SECTION_WARP_INDEX};
        int[] outlines = ArrayUtils.addAll(SECTION_OUTLIINE_INDICES, TEMPLATE_OUTLINE_INDICES);

        new WarpAction("warp section", this).put(actionMap);
        new ToggleDialogAction("help", dialog).put(actionMap);
        new ToggleVisibilityAction("toggle warp", this, sections).put(actionMap);
        new ToggleVisibilityAction("toggle points", this, outlines).put(actionMap);
        new UpdateAction("update section", this).put(actionMap);
        new SectionDifferenceAction("section difference", this).put(actionMap);

        return actionMap;
    }


    public class SectionDifferenceAction extends AbstractNamedAction {

        private final InteractiveAlignmentUi ui;

        SectionDifferenceAction(String name, InteractiveAlignmentUi ui) {
            super(name);
            this.ui = ui;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            this.ui.computeSectionDifference();
        }
    }


    public class ToggleVisibilityAction extends AbstractNamedAction {

        private final int[] indices;
        private final InteractiveAlignmentUi ui;

        ToggleVisibilityAction(String name, InteractiveAlignmentUi ui, int[] sourceIndices) {
            super(name);
            this.ui = ui;
            this.indices = sourceIndices;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            ViewerState viewerState = this.ui.bdvHandle.getViewerPanel().getState();
            VisibilityAndGrouping visibility = this.ui.bdvHandle.getViewerPanel().getVisibilityAndGrouping();
            int nSources = viewerState.getSources().size();
            for (int index : indices) {
                if (index < nSources) {
                    boolean active = viewerState.getSources().get(index).isActive();
                    visibility.setSourceActive(index, !active);
                }
            }

            bdvHandle.getViewerPanel().requestRepaint();
        }
    }


    public class UpdateAction extends AbstractNamedAction {

        private final InteractiveAlignmentUi ui;

        UpdateAction(String name, InteractiveAlignmentUi ui) {
            super(name);
            this.ui = ui;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            this.ui.updateInputSection();
        }
    }


    public class WarpAction extends AbstractNamedAction {

        private final InteractiveAlignmentUi ui;

        WarpAction(String name, InteractiveAlignmentUi ui) {
            super(name);
            this.ui = ui;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            this.ui.warpSectionImage();
        }
    }

    // TODO: messages to BDV or IJ main ui are only triggered at the end of the short-cut callback. Spawn threads?
    private void warpSectionImage() {
        bdvHandle.getViewerPanel().showMessage("mapping sections...");
//        bdvHandle.getViewerPanel().requestRepaint();

        // Extract the outline points from the currently visible section.
        Img<UnsignedShortType> refImg = getCurrentlyVisibleSection(TEMPLATE_INDEX);

        // TODO: given the smoothness of the templates, consider a simpler and faster mask creation
        RandomAccessibleInterval<BitType> refMsk = SectionImageTool.createMask(refImg, ops);
        RandomAccessibleInterval<BitType> refOut = ops.morphology().outline(refMsk, false);

        SectionImageOutline refCon = new SectionImageOutline(refOut, triangulationLevels);

        // Get current section and transform into the global space
        volumeSection = new VolumeSection(new double[]{1, 0, 0},
                new double[]{0, 1, 0},
                new double[]{0, 0, 0});
        AffineTransform3D t_vw = getViewerTransformation();
        AffineTransform3D t_vwi = t_vw.inverse();
        volumeSection = volumeSection.applyTransform(t_vwi);

        // Get the transformation between the input section and the global space
        AffineTransform3D t_wl_s = getSourceTransformation(SECTION_INDEX);
        final InvertibleRealTransformSequence Ts = new InvertibleRealTransformSequence();
        Ts.add(t_wl_s);
        Ts.add(t_vw);
        InvertibleRealTransform Ts_lv = Ts.inverse();
        
        // Map the reference contour on the input section
        Atlas.PlaneOfSection plane = secImg.getPlaneOfSection();
        SectionImageOutline refConMap = refCon.map(Ts_lv, plane);
        refConMap.sample();

//        Img img = ImgView.wrap(refImg, new ArrayImgFactory<>());
//        ImageJFunctions.show(refConMap.visualise());

        ArrayList<SectionImageOutline.OutlinePoint> refPts;
        if (optimizeCorrespondences) {
            refPts = refConMap.getOptimizedCorrespondencePoints(secCon);
        } else {
            refPts = refConMap.getSamples();
        }

        // Get copy of the section points
        ArrayList<SectionImageOutline.OutlinePoint> secPts = secCon.getSamples();

        // remove outliers
        ArrayList<SectionImageOutline.OutlinePoint> secOut = new ArrayList<>();
        if (removeOutliers) {
            double[] distances = new double[secPts.size()];
            for (int i = 0; i < secPts.size(); i++) {
                double[] v1 = secPts.get(i).getCoordinates();
                double[] v2 = refPts.get(i).getCoordinates();

                distances[i] = Math.sqrt(Math.pow(v1[0] - v2[0], 2.) + Math.pow(v1[1] - v2[1], 2.));
            }

            double m = new Mean().evaluate(distances);
            double sd = new StandardDeviation().evaluate(distances, m);
            double d_max = m + 0.5 * sd;
            double d_min = m - 0.5 * sd;//TODO: Parameter (expose?)

            int o = 0;
            for (int i = 0; i < secPts.size(); i++) {
                if (d_min > distances[i] || distances[i] > d_max) {
                    secOut.add(secPts.remove(i));
                    refPts.remove(i);
                    o++;
                }
            }

            if (debug) {
                System.out.println("Removed " + o + " outliers in outline correspondence set.");
            }
        }


        // Put the points in data-structure for the TPS solver and map section outline points and reference points
        // into the same space (local)
        int N = secPts.size();
        double[][] secVects = new double[2][N + 1];
        double[][] refVects = new double[2][N + 1];
        double[] weights = new double[N + 1];
        StringBuilder str = null;

        for (int i = 0; i < (N); i++) {
            if (debug) {
                str = new StringBuilder().append(i).append(":");
            }

            for (int d = 0; d < 2; d++) {
                refVects[d][i] = refPts.get(i).getCoordinates()[d];
                secVects[d][i] = secPts.get(i).getCoordinates()[d];
            }

            weights[i] = 0.5;

            if (debug && (str != null)) {
                str.append(array2str(secPts.get(i).getCoordinates()))
                        .append(" <-> ")
                        .append(array2str(refPts.get(i).getCoordinates()));
                System.out.println(str);
            }
        }

        // Add centroids and weights
        for (int d = 0; d < 2; d++) {
            refVects[d][N] = refConMap.getCentroidCoordinates()[d];
            secVects[d][N] = secCon.getCentroidCoordinates()[d];
        }
        weights[N] = 0.7;

        // Compute the TPS transforms
        ThinPlateR2LogRSplineKernelTransform tps = new ThinPlateR2LogRSplineKernelTransform(2, refVects, secVects, weights);
        t_tps = new TpsTransformWrapper(2, tps);

        // TODO: This should not be necessary, but the inverse of t_tps did not work so far
        ThinPlateR2LogRSplineKernelTransform tpsi = new ThinPlateR2LogRSplineKernelTransform(2, secVects, refVects, weights);
        t_tpsi = new TpsTransformWrapper(2, tpsi);

//        Affine3DHelpers.extractScale()

        RandomAccessibleInterval<V> rai = secImg.copy();
        RealRandomAccessible<V> interp = Views.interpolate(Views.extendZero(rai), new NLinearInterpolatorFactory<>());
        RealRandomAccessible<V> mapped = RealViews.transform(interp, t_tps);
        RandomAccessibleInterval<V> warp = Views.interval(Views.raster(mapped), secImg);

        RandomAccessibleInterval secVol = Views.addDimension(warp, 0, dims.dimension(0) - 1);
        RandomAccessibleInterval secVolWrapped = Views.permute(secVol, 0, 2);

        // Remove and re-add the outline points of the template and the warped section image
        if (warpedSectionSource != null) {
            warpedSectionSource.removeFromBdv();
        }
        if (templateOutlineSrc2 != null) {
            templateOutlineSrc2.removeFromBdv();
        }
        if (templateOutlineSrc1 != null) {
            templateOutlineSrc1.removeFromBdv();
        }
        if (sectionOutlierSrc != null) {
            sectionOutlierSrc.removeFromBdv();
        }

        boolean showPoints = bdvHandle.getViewerPanel().getState().getSources().get(SECTION_OUTLIINE_INDICES[0]).isActive();
        VolumeSection vs = volumeSection.applyTransform(t_wl_s.inverse());
        double section = vs.getP()[plane.getFixedAxisIndex()];

        // Create the point overlays
        templateOutlineSrc1 = BdvFunctions.showOverlay(
                new SectionImageOutlinePoints(refPts, section, plane),
                "template outline points",
                Bdv.options().addTo(bdvHandle).sourceTransform(t_wl_s));
        templateOutlineSrc1.setColor(new ARGBType(0xbfffe0));
        templateOutlineSrc1.setDisplayRangeBounds(0, 1);
        templateOutlineSrc1.setActive(showPoints);

        templateOutlineSrc2 = BdvFunctions.showOverlay(
                new SectionImageOutlinePoints(refConMap.getCentroidCoordinates(), refPts.get(0).getCoordinates(), section, plane),
                "template centroid and 1st",
                Bdv.options().addTo(bdvHandle).sourceTransform(t_wl_s));
        templateOutlineSrc2.setColor(new ARGBType(0xFFFF00));
        templateOutlineSrc2.setDisplayRangeBounds(1, 1);
        templateOutlineSrc2.setActive(showPoints);

        // Add the warped section
        sectionSource.setActive(false);

        warpedSectionSource = BdvFunctions.show(secVolWrapped,
                "warped section",
                Bdv.options().addTo(bdvHandle).sourceTransform(Ts_init));
        warpedSectionSource.setColor(new ARGBType(0x6f6f6f));
        warpedSectionSource.setActive(true);
        warpedSectionSource.setDisplayRange(0, secMaxInt);

        // Add outliers visualization
        if (secOut.size() > 0) {
            SectionImageOutlinePoints outlierPts = new SectionImageOutlinePoints(secOut, section, plane,
                    false, SectionImageOutlinePoints.PointMarker.CROSS);
            outlierPts.setMaxPointSize(6);
            templateOutlineSrc1 = BdvFunctions.showOverlay(
                    outlierPts,
                    "outlier points",
                    Bdv.options().addTo(bdvHandle).sourceTransform(t_wl_s));
            templateOutlineSrc1.setColor(new ARGBType(0x000000));
            templateOutlineSrc1.setDisplayRangeBounds(0, 1);
            templateOutlineSrc1.setActive(showPoints);
        }

        bdvHandle.getViewerPanel().requestRepaint();
        updateInputSection();
    }

    private static String array2str(double[] a) {
        NumberFormat twodec = new DecimalFormat("#0.0");
        StringBuilder str = new StringBuilder();
        for (double v : a) {
            str.append(" ").append(twodec.format(v));
        }

        return str.toString();
    }

    private void computeSectionDifference() {
        AffineTransform3D t = getSourceTransformation(TEMPLATE_INDEX);
        double[] p = new double[3];

        t.inverse().apply(volumeSection.getP(), p);
        Atlas.PlaneOfSection plane = secImg.getPlaneOfSection();
        double section = p[plane.getFixedAxisIndex()];

        Img<UnsignedShortType> img1;
        boolean isWarped = !bdvHandle.getViewerPanel().getState().getSources().get(SECTION_INDEX).isActive();
        if (isWarped) {
            img1 = getCurrentlyVisibleSection(SECTION_WARP_INDEX);
        } else {
            img1 = getCurrentlyVisibleSection(SECTION_INDEX);
        }

        Img<UnsignedShortType> img2 = getCurrentlyVisibleSection(TEMPLATE_INDEX);

        float nssd = SectionImageTool.normalizedSSD(img1, img2, ops);

        String line = secImg.getSource() + ";" + plane + ";" + section + ";" + nssd + ";" + isWarped + "\n";
        System.out.print(line);

        File file = new File("/Users/turf/Desktop/interactive-alignment-results.csv");
        if (!file.exists()) {
            try {
                Files.write(Paths.get(file.getAbsolutePath()), "path;plane;section;NSSD;warped\n".getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            Files.write(Paths.get(file.getAbsolutePath()), line.getBytes(), StandardOpenOption.APPEND);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private AffineTransform3D getViewerTransformation() {
        ViewerState viewerState = bdvHandle.getViewerPanel().getState();
        AffineTransform3D t = new AffineTransform3D();
        viewerState.getViewerTransform(t);

        return t;
    }

    private AffineTransform3D getSourceTransformation(int srcIndex) {
        SourceState srcState = this.bdvHandle.getViewerPanel().getState().getSources().get(srcIndex);
        AffineTransform3D t = new AffineTransform3D();
        srcState.getSpimSource().getSourceTransform(0, 0, t);

        return t;
    }

    private Img<UnsignedShortType> getCurrentlyVisibleSection(int srcIndex) {
        ViewerState viewerState = bdvHandle.getViewerPanel().getState();

        int yMax = bdvHandle.getViewerPanel().getDisplay().getHeight();
        int xMax = bdvHandle.getViewerPanel().getDisplay().getWidth();
        long[] lb = new long[]{0, 0, 0};
        long[] ub = new long[]{xMax, yMax, 0};

        final AffineTransform3D T_vw = new AffineTransform3D();
        bdvHandle.getViewerPanel().getState().getViewerTransform(T_vw);

        SourceState srcState = viewerState.getSources().get(srcIndex);
        final AffineTransform3D T_wl = new AffineTransform3D();
        srcState.getSpimSource().getSourceTransform(0, 0, T_wl);

        final InvertibleRealTransformSequence T_vl = new InvertibleRealTransformSequence();
        T_vl.add(T_wl);
        T_vl.add(T_vw);
        //TODO: add some scaling to compensate for the viewer size and keep the section size for outline point extraction constant

        RandomAccessibleInterval<UnsignedShortType> rai = srcState.getSpimSource().getSource(0, 0);
        RealRandomAccessible<UnsignedShortType> interpolated = Views.interpolate(Views.extendZero(rai), new NLinearInterpolatorFactory<>());
        RealRandomAccessible<UnsignedShortType> transformed = RealViews.transform(interpolated, T_vl);
        RandomAccessibleInterval<UnsignedShortType> section = Views.interval(Views.raster(transformed), lb, ub);

        return ImgView.wrap(Views.hyperSlice(section, 2, 0), new ArrayImgFactory<>());
    }

//    private List<long[]> getBounds(RandomAccessibleInterval rai, InvertibleRealTransformSequence transform) {
//        int n = rai.numDimensions();
//        double[] ulc = new double[]{0, 0, 0};
//        double[] lrc = new double[]{0L, rai.dimension(1) - 1, rai.dimension(2) - 1};
//        double[] lb = new double[n];
//        double[] ub = new double[n];
//        transform.apply(ulc, lb);
//        transform.apply(lrc, ub);
//
//        long[] lbl = new long[n];
//        long[] ubl = new long[n];
//        for (int i = 0; i < n; i++) {
//            lbl[i] = (long) lb[i];
//            ubl[i] = (long) ub[i];
//        }
//
////            lbl[n - 1] = 0;
////            ubl[n - 1] = 0;
//
//        List<long[]> bounds = new ArrayList<>(2);
//        bounds.add(lbl);
//        bounds.add(ubl);
//
//        return bounds;
//    }
}
