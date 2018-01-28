package gui;

import gui.bdv.SectionImageOutlinePoints;
import img.AraImgPlus;
import img.SectionImageOutlineSampler;
import img.SectionImageTool;
import img.VolumeSection;
import rest.AllenRefVol;

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

import jitk.spline.ThinPlateR2LogRSplineKernelTransform;

import org.scijava.app.StatusService;
import org.scijava.ui.behaviour.KeyStrokeAdder;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.AbstractNamedAction;

import net.imagej.ImageJ;
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

import javax.swing.JFrame;
import javax.swing.InputMap;
import javax.swing.ActionMap;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.ArrayUtils;
import rest.Atlas;

/**
 * TODO: Add possibility to work with different perspectives: coronal works, sagital and horizontal need to be added [1]
 * TODO: Make it so that the section volume AND the template volume can be transformed. Currently transforming the section volume results in wrong TPS wraps
 *
 * @author Felix Meyenhofer
 */
public class InteractiveAlignmentUi<V extends RealType<V> & NativeType<V>>  {

    private final StatusService status;
    private final OpService ops;


    private static int DISPLAY_WIDTH = 652;
    private static int DISPLAY_HEIGHT = 512;
    private static int SECTION_INDEX = 0;
    private static int[] SECTION_OUTLIINE_INDICES = new int[]{1, 2};
    private static int SECTION_WARP_INDEX = 6;
    private static int TEMPLATE_INDEX = 3;
    private static int[] TEMPLATE_OUTLINE_INDICES = new int[]{4, 5};

    private boolean debug = false;

//    private boolean interpolate = true;
//    private int resolutionOutput = 64;
    private int triangulationLevels = 4;


    private BdvHandlePanel bdvHandle;
    private static BdvSource sectionSource = null;
    private static BdvSource warpedSectionSource = null;
    private static BdvSource sectionOutlineSrc1 = null;
    private static BdvSource sectionOutlineSrc2 = null;
    private static BdvSource templateOutlineSrc1 = null;
    private static BdvSource templateOutlineSrc2 = null;

    private final AraImgPlus<V> secImg;
    private RandomAccessibleInterval secVol;
    private final AllenRefVol refVol;
    private final Dimensions dims;
    private final AffineTransform3D Tr_init;
    private AffineTransform3D Ts_init;
    private VolumeSection volumeSection;

    private final SectionImageOutlineSampler secCon;
    private final ArrayList<SectionImageOutlineSampler.OutlinePoint> secPts;
    private TpsTransformWrapper t_tps;
    private TpsTransformWrapper t_tpsi;


    public InteractiveAlignmentUi(AraImgPlus<V> secImg, AllenRefVol refVolFile, OpService opService, StatusService statusService)
            throws SpimDataException {
        this.ops = opService;
        this.status = statusService;
        this.secImg = secImg;
        this.refVol = refVolFile;
        this.dims = refVol.getDimensions();
        this.Tr_init = refVol.getTransform();

        secVol = secImg.createSectionVolume(refVol);

        // Extract the outline points of the input section (once!)
        status.showStatus(0,100, "create section mask");
        RandomAccessibleInterval<BitType> secMsk = SectionImageTool.createMask(secImg, ops);
        status.showStatus(60, 100, "create section outline points");
        RandomAccessibleInterval<BitType> secOut = ops.morphology().outline(secMsk, false);
        secCon = new SectionImageOutlineSampler(secOut, triangulationLevels);
        secCon.generatePoints();
        secPts = secCon.getCorrespondencePoints();
        status.showStatus(100, 100, "done loading section image");
    }

    private void updateInputSection() {
        ViewerState state = bdvHandle.getViewerPanel().getState();

        AffineTransform3D tr = new AffineTransform3D();
        SourceState refState = state.getSources().get(TEMPLATE_INDEX);
        refState.getSpimSource().getSourceTransform(0, 0, tr);

        AffineTransform3D ts = new AffineTransform3D();
        SourceState secState = state.getSources().get(SECTION_INDEX);
        secState.getSpimSource().getSourceTransform(0, 0, ts);
        
        secImg.updateRegistrationInfo(t_tps, t_tpsi, ts, tr, volumeSection);
    }

    public void createAndShow() throws SpimDataException {
        JFrame window = new JFrame("Interactive Section Alignment");
        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                updateInputSection();
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
                InteractiveAlignmentUi.class.getResource("/bdv/InteractiveAlignmentHelp.html"));
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
        sectionSource.setColor(new ARGBType(0x0000FF));
        sectionSource.setActive(true);

        // Add section contour points
        double x = dims.dimension(0)/2;

        sectionOutlineSrc1 = BdvFunctions.showOverlay(
                new SectionImageOutlinePoints(secPts, x, AllenRefVol.Plane.YZ),
                "section outline points",
                Bdv.options().addTo(bdvHandle).sourceTransform(Ts_init));
        sectionOutlineSrc1.setColor(new ARGBType(0x0000FF));
        sectionOutlineSrc1.setDisplayRangeBounds(0, 1);
        sectionOutlineSrc1.setActive(false);

        sectionOutlineSrc2 = BdvFunctions.showOverlay(
                new SectionImageOutlinePoints(secCon.getCentroidCoordinates(), secPts.get(0).getCoordinates(), x, AllenRefVol.Plane.YZ),
                "section centroid and 1st",
                Bdv.options().addTo(bdvHandle).sourceTransform(Ts_init));
        sectionOutlineSrc2.setColor(new ARGBType(0x00FFFF));
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
        tvw.rotate(1, Math.PI/2);   // TODO: [1]

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
        double[] dcen = new double[]{DISPLAY_WIDTH/2 - cen_t[0], DISPLAY_HEIGHT/2 - cen_t[1], -cen_t[2]};
        tvw.translate(dcen);
        bdvHandle.getViewerPanel().setCurrentViewerTransform(tvw);
    }

    private InputMap createInputMap(final KeyStrokeAdder.Factory keyProperties) {
        final InputMap inputMap = new InputMap();
        final KeyStrokeAdder map = keyProperties.keyStrokeAdder( inputMap );

        map.put("warp section", "W");
        map.put("toggle warp", "shift W");
        map.put("toggle points", "shift P");
        map.put("update section", "shift U");
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

        return actionMap;
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
        Img<UnsignedShortType> refImg = getCurrentlyVisibleTemplateSection();

        // TODO: given the smoothness of the templates, consider a simpler and faster mask creation
        RandomAccessibleInterval<BitType> refMsk = SectionImageTool.createMask(refImg, ops);
        RandomAccessibleInterval<BitType> refOut = ops.morphology().outline(refMsk, false);

        SectionImageOutlineSampler refCon = new SectionImageOutlineSampler(refOut, triangulationLevels);
        refCon.generatePoints();
        ArrayList<SectionImageOutlineSampler.OutlinePoint> refPts = refCon.getCorrespondencePoints();

        // TODO optimize correspondences

        // Transform the outline points back on the input section.
        ViewerState viewerState = bdvHandle.getViewerPanel().getState();
        AffineTransform3D t_vw = new AffineTransform3D();
        viewerState.getViewerTransform(t_vw);

        // compute the current plane in the global space
        AffineTransform3D t_vwi = t_vw.inverse();

        volumeSection = new VolumeSection(new double[]{1, 0, 0},
                new double[]{0, 1, 0},
                new double[]{0, 0, 0});
        volumeSection.applyTransform(t_vwi);

        SourceState srcState = viewerState.getSources().get(SECTION_INDEX);
        AffineTransform3D t_wl_s = new AffineTransform3D();
        srcState.getSpimSource().getSourceTransform(0, 0, t_wl_s);

        final InvertibleRealTransformSequence Ts = new InvertibleRealTransformSequence();
        Ts.add(t_wl_s);
        Ts.add(t_vw);
        InvertibleRealTransform Ts_lv = Ts.inverse();

        // Put the points in data-structure for the TPS solver and map section outline points and reference points
        // into the same space (local)
        int N = secPts.size();
        double[][] secVects = new double[2][N];
        double[][] refVects = new double[2][N];
        StringBuilder str = null;

        for (int i = 0; i < N; i++) {
            if (debug) {
                str = new StringBuilder().append(i).append(":");
            }

            double[] srcVect = new double[]{refPts.get(i).getCoordinates()[0], refPts.get(i).getCoordinates()[1], 0};
            double[] dstVect = new double[3];
            Ts_lv.apply(srcVect, dstVect);

            double[] secVect = new double[]{secPts.get(i).getCoordinates()[0], secPts.get(i).getCoordinates()[1]};  // TODO: [1]
            for (int d = 0; d < 2; d++) {
                refVects[d][i] = dstVect[2-d]; // TODO: [1]
                secVects[d][i] = secVect[d];
            }

            if (debug && (str != null)) {
                str.append(array2str(srcVect))
                        .append( " <-> ")
                        .append(array2str(new double[]{ refVects[0][i], refVects[1][i]}))
                        .append(" - ")
                        .append(array2str(dstVect));
                System.out.println(str);
            }
        }

        ThinPlateR2LogRSplineKernelTransform tps = new ThinPlateR2LogRSplineKernelTransform(2, refVects, secVects);
        t_tps = new TpsTransformWrapper(2, tps);

        // TODO: This should not be necessary, but the inverse of t_tps did not work so far
        ThinPlateR2LogRSplineKernelTransform tpsi = new ThinPlateR2LogRSplineKernelTransform(2, secVects, refVects);
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

        boolean showPoints = bdvHandle.getViewerPanel().getState().getSources().get(SECTION_OUTLIINE_INDICES[0]).isActive();

        // Transform the points from the reference section back to the reference volume
        SourceState refState = viewerState.getSources().get(TEMPLATE_INDEX);
        AffineTransform3D t_wl_r = new AffineTransform3D();
        refState.getSpimSource().getSourceTransform(0, 0, t_wl_s);
        final InvertibleRealTransformSequence Tr = new InvertibleRealTransformSequence();
        Tr.add(t_wl_r);
        Tr.add(t_vw);

        InvertibleRealTransform Tr_lv = Tr.inverse();

        // Create the point overlays
        templateOutlineSrc1 = BdvFunctions.showOverlay(
                new SectionImageOutlinePoints(refPts, Tr_lv),
                "template outline points",
                Bdv.options().addTo(bdvHandle).sourceTransform(t_wl_r));
        templateOutlineSrc1.setColor(new ARGBType(0xFF0000));
        templateOutlineSrc1.setDisplayRangeBounds(0, 1);
        templateOutlineSrc1.setActive(showPoints);

        templateOutlineSrc2 = BdvFunctions.showOverlay(
                new SectionImageOutlinePoints(refCon.getCentroidCoordinates(), refPts.get(0).getCoordinates(), Tr_lv),
                "template centroid and 1st",
                Bdv.options().addTo(bdvHandle).sourceTransform(t_wl_r));
        templateOutlineSrc2.setColor(new ARGBType(0xFFFF00));
        templateOutlineSrc2.setDisplayRangeBounds(1, 1);
        templateOutlineSrc2.setActive(showPoints);

        // Add the warped section
        sectionSource.setActive(false);

        warpedSectionSource = BdvFunctions.show(secVolWrapped,
                "warped section",
                Bdv.options().addTo(bdvHandle).sourceTransform(Ts_init));
        warpedSectionSource.setColor(new ARGBType(0x0000FF));
        warpedSectionSource.setActive(true);

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

    private Img<UnsignedShortType> getCurrentlyVisibleTemplateSection() {
        ViewerState viewerState = bdvHandle.getViewerPanel().getState();

        int yMax = bdvHandle.getViewerPanel().getDisplay().getHeight();
        int xMax = bdvHandle.getViewerPanel().getDisplay().getWidth();
        long[] lb = new long[]{0, 0, 0};
        long[] ub = new long[]{xMax, yMax, 0};

        final AffineTransform3D T_vw = new AffineTransform3D();
        bdvHandle.getViewerPanel().getState().getViewerTransform(T_vw);

        SourceState srcState = viewerState.getSources().get(TEMPLATE_INDEX);
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


    public static void main(String[] args) throws SpimDataException, IOException {
        ImageJ ij = new ImageJ();
        ij.ui().showUI();
        //        String refPath = "/Users/turf/switchdrive/SJMCS/data/aba/mouse-ccf-3/reference-volumes/average_template_25.nrrd";
        String refPath = "/Users/turf/allen-cache/reference-volumes/average_template_25.nrrd";
        String secPath = "/Users/turf/switchdrive/SJMCS/data/devel/small-deformations/crym(cy3)_gng2(A488)_IHC(150914)_DGC4_1 - 2016-01-28 05.03.56-FITC_ROI-00.tif";

//        File refFile = new File(refPath);
//        Nrrd_Reader nrrd = new Nrrd_Reader();
//        ImagePlus imp = nrrd.load(refFile.getParent(), refFile.getName());
//        RandomAccessibleInterval rai1 = ImageJFunctions.wrap(imp);
//        RandomAccessibleInterval refImg = Views.permute(rai1, 0, 2);

        AllenRefVol refVol = new AllenRefVol(new File(refPath));
//        for (int d = 0; d < refDims.numDimensions(); d++) {
//            System.out.println(refDims.dimension(d));
//        }
//        System.out.println(refDims.toString());

        /*
        RandomAccessibleInterval rai2 = (RandomAccessibleInterval) ij.io().open(secPath);//ImageJFunctions.wrap(imp);
        RandomAccessibleInterval secVol = Views.addDimension(rai2, 0, xMax);
        RandomAccessibleInterval secVol = Views.permute(secVol, 0, 2);
        */

        Img secImg = (Img) ij.io().open(secPath);
        AraImgPlus ara = new AraImgPlus(secImg, 1, Atlas.PlaneOfSection.CORONAL, Atlas.VoxelResolution.TWENTYFIVE);

//        ij.command().run(InteractiveAlignmentUi.class, true);
        InteractiveAlignmentUi ui = new InteractiveAlignmentUi(ara, refVol, ij.op(), ij.status());
        ui.createAndShow();
    }
}
