package gui;

import img.SectionImageOutlineSampler;
import img.SectionImageTool;
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

import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.ViewId;

import jitk.spline.ThinPlateR2LogRSplineKernelTransform;

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

import javax.swing.JFrame;
import javax.swing.InputMap;
import javax.swing.ActionMap;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * TODO: Add outline points as overlays
 * TODO: Add possibility to work with different perspectives: coronal works, sagital and horizontal need to be added
 * TODO: Set initial perspective
 *
 * @author Felix Meyenhofer
 */
public class InteractiveAlignmentUi<V extends RealType<V>>  {

    private OpService ops;

    private static int DISPLAY_WIDTH = 652;
    private static int DISPLAY_HEIGHT = 512;


//    private boolean interpolate = true;
//    private int resolutionOutput = 64;
    private int triangulationLevels = 4;


    private BdvHandlePanel bdvHandle;
    private BdvSource sectionSource;

    private final Img<V> secImg;
    private RandomAccessibleInterval secVolWrapped;
    private RandomAccessibleInterval secVolPer;
    private final ArrayList<SectionImageOutlineSampler.OutlinePoint> secPts;
    private final AllenRefVol refVol;
    private final Dimensions dims;

    private final AffineTransform3D initialTransform;
    private boolean wrapped = false;


    private InteractiveAlignmentUi(Img<V> secImg, AllenRefVol refVolFile, OpService opService) throws SpimDataException {
        this.ops = opService;
        this.secImg = secImg;
        this.refVol = refVolFile;
        this.dims = refVol.getHdf5().getSequenceDescription().getViewSetups().get(0).getSize();
        this.initialTransform = refVol.getHdf5().getViewRegistrations().getViewRegistration(new ViewId(0, 0)).getModel().copy();

        // TODO: depends on the input section (xy, yz, zx)
        RandomAccessibleInterval secVol = Views.addDimension(secImg, 0, dims.dimension(0) - 1);
        secVolPer = Views.permute(secVol, 0, 2);

        RandomAccessibleInterval<BitType> secMsk = SectionImageTool.createMask(secImg, ops);
        RandomAccessibleInterval<BitType> secOut = ops.morphology().outline(secMsk, false);
        SectionImageOutlineSampler secCon = new SectionImageOutlineSampler(secOut, triangulationLevels);
        secCon.generatePoints();
        secPts = secCon.getCorrespondencePoints();
//        srcPts = getGlobalCoordinates(secPts, initialTransform);
    }

    private void createAndShow() throws SpimDataException {
        JFrame window = new JFrame("Interactive Section Alignment");

        // General options
        BdvOptions options = Bdv.options();
        options.axisOrder(AxisOrder.XYZ);
        bdvHandle = new BdvHandlePanel(window, options);

        // Add key binding
        InputTriggerConfig triggerConfig = BigDataViewer.getInputTriggerConfig(ViewerOptions.options());
        bdvHandle.getKeybindings().addInputMap("bdv ia", createInputMap(triggerConfig));
        HelpDialog dialog = new HelpDialog(window,
                InteractiveAlignmentUi.class.getResource("/bdv/InteractiveAlignmentHelp.html"));
        bdvHandle.getKeybindings().addActionMap("bdv ia", createActionMap(dialog));

        // Configure the UI window
        window.add(bdvHandle.getViewerPanel(), BorderLayout.CENTER);

        window.setBounds(50, 50, DISPLAY_WIDTH, DISPLAY_HEIGHT);
        window.setVisible(true);

        // Add the reference volume
        List<BdvStackSource<?>> ref = BdvFunctions.show(refVol.getHdf5(),
                Bdv.options().addTo(bdvHandle));
        ref.get(0).setColor(new ARGBType(0x00FF00));
        ref.get(0).setActive(true);
//        ref.get(0).setDisplayRange(0, 400);

        // Add the section volume
        sectionSource = BdvFunctions.show(secVolPer,
                "New Section",
                Bdv.options().addTo(bdvHandle).sourceTransform(initialTransform));
        sectionSource.setColor(new ARGBType(0x0000FF));
        sectionSource.setActive(true);

        // Get the current view transform and rotate it to the yz-plane
        AffineTransform3D tvw = new AffineTransform3D();
        bdvHandle.getViewerPanel().getState().getViewerTransform(tvw);
        tvw.rotate(1, Math.PI/2);   // TODO: depends on the input section (xy, yz, zx)

        // Assemble the entire transform from source to display
        InvertibleRealTransformSequence tlv = new InvertibleRealTransformSequence();
        tlv.add(initialTransform);
        tlv.add(tvw);

        // Check where the center is and put it in the middle of the display
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
        map.put("help", "F1", "H");

        return inputMap;
    }

    private ActionMap createActionMap(HelpDialog dialog) {
        final ActionMap actionMap = new ActionMap();

        new WarpAction("warp section", this).put(actionMap);
        new ToggleDialogAction("help", dialog).put(actionMap);
        new ToggleWarpAction("toggle warp", this).put(actionMap);

        return actionMap;
    }

    private void toggleWarp() {
        RandomAccessibleInterval rai = (wrapped) ? secVolPer : secVolWrapped;

        if (rai == null) {
            bdvHandle.getViewerPanel().showMessage("Register with <W>, before toggling.");
        } else {
            sectionSource.removeFromBdv();
            sectionSource = BdvFunctions.show(rai,
                    "New Section",
                    Bdv.options().addTo(bdvHandle).sourceTransform(initialTransform));
            sectionSource.setColor(new ARGBType(0x0000FF));
            sectionSource.setActive(true);
            wrapped = !wrapped;
        }
        bdvHandle.getViewerPanel().requestRepaint();
    }

    public class ToggleWarpAction extends AbstractNamedAction {

        private final InteractiveAlignmentUi ui;

        ToggleWarpAction(String name, InteractiveAlignmentUi ui) {
            super(name);
            this.ui = ui;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            this.ui.toggleWarp();
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
//            this.ui.register();
            this.ui.snapOutline();
        }
    }


//    private <U extends RealType<U> & NativeType<U>> ImagePlus warp(Img<U> source, Img<U> target) throws Exception {
//        long[] dim = new long[source.numDimensions()];
//        source.dimensions(dim);
//        int width = (int) dim[0];
//        int height = (int) dim[1];
//
//        RandomAccessibleInterval<BitType> secMsk = SectionImageTool.createMask(source, ops);
//        RandomAccessibleInterval<BitType> refMsk = SectionImageTool.createMask(target, ops);
//
//        RandomAccessibleInterval<BitType> secOut = ops.morphology().outline(secMsk, false);
//        RandomAccessibleInterval<BitType> refOut = ops.morphology().outline(refMsk, false);
//
//        SectionImageOutlineSampler secCon = new SectionImageOutlineSampler(secOut, triangulationLevels);
//        secCon.generatePoints();
//        SectionImageOutlineSampler refCon = new SectionImageOutlineSampler(refOut, triangulationLevels);
//        refCon.generatePoints();
//
//        // Alignment
//        secCon.optimize(refCon);
//        ArrayList<SectionImageOutlineSampler.OutlinePoint> secPts = secCon.getCorrespondencePoints();
//        ArrayList<SectionImageOutlineSampler.OutlinePoint> refPts = refCon.getCorrespondencePoints();
//
//        int nMatches = refPts.size() + 1;
//        ArrayList<PointMatch> matches = new ArrayList<>(nMatches);
//        for (int i = 0; i < refPts.size(); i++) {
//            Point p1 = new Point(secPts.get(i).getCoordinates());
//            Point p2 = new Point(refPts.get(i).getCoordinates());
//            matches.add(new PointMatch(p1, p2));
//        }
//
//        matches.add(new PointMatch(new Point(secCon.getCentroidCoordinates()),
//                new Point(refCon.getCentroidCoordinates()), 3));
//
//        final MovingLeastSquaresTransform mlt = new MovingLeastSquaresTransform();
//        mlt.setModel(AffineModel2D.class);
//        mlt.setAlpha(2.0f);
//        mlt.setMatches(matches);
//
//        CoordinateTransformMesh mltMesh = new CoordinateTransformMesh(mlt, resolutionOutput, width, height);
//        final TransformMeshMapping<CoordinateTransformMesh> mltMapping = new TransformMeshMapping<>(mltMesh);
//
//        final ImagePlus secImp = ImageJFunctions.wrap(source, "section");
//        final ImageProcessor srcIp, trgIp;
//        srcIp = secImp.getProcessor();
//        trgIp = srcIp.createProcessor(width, height);
//
//        if (interpolate) {
//            mltMapping.mapInterpolated(srcIp, trgIp);
//        } else {
//            mltMapping.map(srcIp, trgIp);
//        }
//
//        ImagePlus warp = new ImagePlus("mapped section", trgIp);
//
//        return warp;
//    }

//    private <M extends RealType<M> & NativeType<M>> void visualiseWarp(Img<M> source, ImagePlus warp, Img<M> target) {
//        long[] dim = new long[source.numDimensions()];
//        source.dimensions(dim);
//
//        M miSrc = source.firstElement().createVariable();
//        miSrc.setReal(0.0);
//        M maSrc = ops.stats().max(source);
//        M maSrcT = source.firstElement().createVariable();
//        maSrcT.setReal(255.0);
//        Img<UnsignedByteType> nSrc = ops.convert().uint8(ops.image().normalize(source, miSrc, maSrc, miSrc, maSrcT));
//
//        M miTar = target.firstElement().createVariable();
//        miTar.setReal(0.0);
//        M maTar = ops.stats().max(target);
//        M maTarT = target.firstElement().createVariable();
//        maTarT.setReal(255.0);
//        Img<UnsignedByteType> nTar = ops.convert().uint8(ops.image().normalize(target, miTar, maTar, miTar, maTarT));
//
//        final ImagePlus secImp = ImageJFunctions.wrap(nSrc, "source");
//        final ImagePlus refImp = ImageJFunctions.wrap(nTar, "target");
//
//
//        ImageStack stk = new ImageStack(secImp.getWidth(), secImp.getHeight(), 3);
//        stk.setProcessor(secImp.getProcessor(), 1);
//        stk.setProcessor(warp.getProcessor(), 2);
//        stk.setProcessor(refImp.getProcessor(), 3);
//
//        CompositeImage res = new CompositeImage(new ImagePlus("warp", stk), CompositeImage.COMPOSITE);
//        res.show();
//    }

//    private void register() {
//        Img<UnsignedShortType> refImg = getCurrentlyVisibleSection(0, bdvHandle);//ImgView.wrap(refSec, new ArrayImgFactory<>());
//        Img<UnsignedShortType> secImg = getCurrentlyVisibleSection(1, bdvHandle);//ImgView.wrap(newSec, new ArrayImgFactory<>());
////        ImageJFunctions.show(refImg);
////            SectionImageAlignment<UnsignedShortType> alignment = new SectionImageAlignment<>(secImg.copy(), refImg.copy(), 64, true, 5, ops);
//        try {
//            ImagePlus mapped = warp(secImg, refImg);
//            visualiseWarp(secImg, mapped, refImg);
//        } catch (Exception e1) {
//            e1.printStackTrace();
//        }
//
//        System.out.println("was here");
//    }

    private void snapOutline() {
        bdvHandle.getViewerPanel().showMessage("mapping sections...");
        bdvHandle.getViewerPanel().requestRepaint();

        Img<UnsignedShortType> refImg = getCurrentlyVisibleSection(0, bdvHandle);

        // TODO: given the smoothness of the templates, consider a simpler and faster mask creation
        RandomAccessibleInterval<BitType> refMsk = SectionImageTool.createMask(refImg, ops);
        RandomAccessibleInterval<BitType> refOut = ops.morphology().outline(refMsk, false);
//        ImageJFunctions.show(refOut, "ref. outline");

        SectionImageOutlineSampler refCon = new SectionImageOutlineSampler(refOut, triangulationLevels);
        refCon.generatePoints();
        ArrayList<SectionImageOutlineSampler.OutlinePoint> refPts = refCon.getCorrespondencePoints();

        ViewerState viewerState = bdvHandle.getViewerPanel().getState();
        AffineTransform3D T_vw = new AffineTransform3D();
        viewerState.getViewerTransform(T_vw);

        SourceState srcState = viewerState.getSources().get(0);
        final AffineTransform3D T_wl = new AffineTransform3D();
        srcState.getSpimSource().getSourceTransform(0, 0, T_wl);

        final InvertibleRealTransformSequence T = new InvertibleRealTransformSequence();
        T.add(T_wl);
        T.add(T_vw);

        InvertibleRealTransform T_lv = T.inverse();

//        int yMax = bdvHandle.getViewerPanel().getDisplay().getHeight();
//        int xMax = bdvHandle.getViewerPanel().getDisplay().getWidth();
//        double[] zsample = new double[3];
//        T_lv.apply(new double[]{xMax / 2, yMax / 2, 0}, zsample);
////        double z = zsample[2];
//
////        System.out.println(T);
        int N = secPts.size();


        double[][] secVects = new double[2][N];
        double[][] refVects = new double[2][N];
        NumberFormat twodec = new DecimalFormat("#0.0");

        for (int i = 0; i < N; i++) {
            StringBuffer str1 = new StringBuffer().append(i).append(":");
            StringBuilder str2 = new StringBuilder();

            double[] srcVect = new double[]{refPts.get(i).getCoordinates()[0], refPts.get(i).getCoordinates()[1], 0};
            double[] dstVect = new double[3];
            T_lv.apply(srcVect, dstVect);

            double[] secVect = new double[]{secPts.get(i).getCoordinates()[0], secPts.get(i).getCoordinates()[1]};  // TODO: depends on the input section (xy, yz, zx)

            for (int d = 0; d < 2; d++) {
                refVects[d][i] = dstVect[2-d]; // TODO: depends on the input section (xy, yz, zx)
                str2.append(" ").append(twodec.format(dstVect[2-d]));

                secVects[d][i] = secVect[d];
                str1.append(" ").append(twodec.format(secVect[d]));
            }
            str1.append(" <-> ").append(str2).append(" - ");
            System.out.print(str1);

            System.out.println(array2str(dstVect));
        }

//        if (secCon == null) {
//
//            RealRandomAccessible<V> rai = Views.interpolate(Views.extendZero(secImg), new NLinearInterpolatorFactory<>());
//            RealRandomAccessible<V> mapped = RealViews.transform(rai, initialTransform);
//            secImgGlob = Views.raster(mapped);
//
//            RandomAccessibleInterval<BitType> secMsk = SectionImageTool.createMask(secImg, ops);
//            RandomAccessibleInterval<BitType> secOut = ops.morphology().outline(secMsk, false);
//            secCon = new SectionImageOutlineSampler(secOut, triangulationLevels);
//            secCon.generatePoints();
////        IntType secArea = ops.stats().sum(secImg);
//            ImageJFunctions.show(secOut);
//        }

//        ArrayList<SectionImageOutlineSampler.OutlinePoint> secPts = secCon.getOptimizedCorrespondencePoints(refCon);


//        int N = refPts.size();
//        double[][] srcPts = new double[2][N];
//        double[][] dstPts = new double[2][N];
//        for (int i = 0; i < refPts.size(); i++) {
//            for (int d = 0; d < 2; d++) {
//                srcPts[d][i] = secPts.get(i).getCoordinates()[d];
//                dstPts[d][i] = refPts.get(i).getCoordinates()[d];
//            }
//        }

        ThinPlateR2LogRSplineKernelTransform tps = new ThinPlateR2LogRSplineKernelTransform(2, refVects, secVects);
        TpsTransformWrapper tpsw = new TpsTransformWrapper(2, tps);

//        Affine3DHelpers.extractScale()

//        InvertibleRealTransformSequence T_wl = new InvertibleRealTransformSequence();
//        T_wl.add(tpsw);
//        T_wl.add(this.initialTransform);


        RandomAccessibleInterval<V> rai = secImg.copy();
        RealRandomAccessible<V> interp = Views.interpolate(Views.extendZero(rai), new NLinearInterpolatorFactory<>());
        RealRandomAccessible<V> mapped = RealViews.transform(interp, tpsw);
        RandomAccessibleInterval<V> warp = Views.interval(Views.raster(mapped), secImg);
//        ImageJFunctions.show(warp, "wrapped section");

        RandomAccessibleInterval secVol = Views.addDimension(warp, 0, dims.dimension(0) - 1);
        secVolWrapped = Views.permute(secVol, 0, 2);

        wrapped = false;
        toggleWarp();
//        sectionSource.removeFromBdv();
//        sectionSource = BdvFunctions.show(secVolWrapped,
//                "New Section",
//                Bdv.options().addTo(bdvHandle).sourceTransform(initialTransform));
//        sectionSource.setColor(new ARGBType(0x0000FF));
//
//        bdvHandle.getViewerPanel().requestRepaint();
        System.out.println("mapped sections.");
    }

    private static String array2str(double[] a) {
        NumberFormat twodec = new DecimalFormat("#0.0");
        StringBuilder str = new StringBuilder();
        for (double v : a) {
            str.append(" ").append(twodec.format(v));
        }

        return str.toString();
    }

    private static Img<UnsignedShortType> getCurrentlyVisibleSection(int sourceNumber, BdvHandlePanel bdvHandle) {
        ViewerState viewerState = bdvHandle.getViewerPanel().getState();

        int yMax = bdvHandle.getViewerPanel().getDisplay().getHeight();
        int xMax = bdvHandle.getViewerPanel().getDisplay().getWidth();
        long[] lb = new long[]{0, 0, 0};
        long[] ub = new long[]{xMax, yMax, 0};

        final AffineTransform3D T_vw = new AffineTransform3D();
        bdvHandle.getViewerPanel().getState().getViewerTransform(T_vw);

        SourceState srcState = viewerState.getSources().get(sourceNumber);
        final AffineTransform3D T_wl = new AffineTransform3D();
        srcState.getSpimSource().getSourceTransform(0, 0, T_wl);

        final InvertibleRealTransformSequence T_vl = new InvertibleRealTransformSequence();
        T_vl.add(T_wl);
        T_vl.add(T_vw);
        
        RandomAccessibleInterval<UnsignedShortType> rai = srcState.getSpimSource().getSource(0, 0);
//        ImageJFunctions.show(rai);

        RealRandomAccessible<UnsignedShortType> interpolated = Views.interpolate(Views.extendZero(rai), new NLinearInterpolatorFactory<>());
        RealRandomAccessible<UnsignedShortType> transformed = RealViews.transform(interpolated, T_vl);
//        ImageJFunctions.show(Views.interval(Views.raster(transformed), rai));
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
        RandomAccessibleInterval secVolPer = Views.permute(secVol, 0, 2);
        */

        Img secImg = (Img) ij.io().open(secPath);

//        ij.command().run(InteractiveAlignmentUi.class, true);
        InteractiveAlignmentUi ui = new InteractiveAlignmentUi(secImg, refVol, ij.op());
        ui.createAndShow();
    }
}
