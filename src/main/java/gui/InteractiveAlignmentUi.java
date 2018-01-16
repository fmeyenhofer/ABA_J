package gui;

import bdv.BigDataViewer;
import bdv.tools.HelpDialog;
import bdv.tools.ToggleDialogAction;
import bdv.viewer.ViewerOptions;
import ij.CompositeImage;
import ij.ImageStack;
import ij.process.ImageProcessor;
import img.SectionImageOutlineSampler;
import img.SectionImageTool;
import mpicbg.ij.TransformMeshMapping;
import mpicbg.models.*;
import mpicbg.models.Point;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import org.scijava.ui.behaviour.KeyStrokeAdder;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.AbstractNamedAction;
import rest.AllenRefVol;


import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.ViewId;

import net.imagej.ImageJ;
import net.imagej.ops.OpService;

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
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.RealType;

import ij.ImagePlus;

import bdv.util.*;
import bdv.viewer.state.SourceState;
import bdv.viewer.state.ViewerState;

import javax.swing.JFrame;
import javax.swing.InputMap;
import javax.swing.ActionMap;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Felix Meyenhofer
 */
public class InteractiveAlignmentUi<V extends RealType<V>>  {

    private OpService ops;


    private final RandomAccessibleInterval<V> secImg;
    private final AllenRefVol refVol;


    private boolean interpolate = true;
    private int resolutionOutput = 64;
    private int triangulationLevels = 4;


    private BdvHandlePanel bdvHandle;
    private HelpDialog helpDialog;


    private InteractiveAlignmentUi(RandomAccessibleInterval<V> secImg, AllenRefVol refVolFile, OpService opService) {

        this.secImg = secImg;
        this.refVol = refVolFile;
        this.ops = opService;
    }

    private void createAndShow() throws SpimDataException {
        JFrame window = new JFrame("Interactive Section Alignment");

        helpDialog = new HelpDialog(window, InteractiveAlignmentUi.class.getResource("/bdv/InteractiveAlignmentHelp.html"));
//        helpDialog.setVisible(true);

        // General options
        BdvOptions options = Bdv.options();
        options.axisOrder(AxisOrder.XYZ);
        bdvHandle = new BdvHandlePanel(window, options);

        // Add key binding
//        InputMap inputMap = new InputMap();
//        inputMap.put(KeyStroke.getKeyStroke("W"), "warp section");
//        bdvHandle.getKeybindings().addInputMap("warp action", inputMap);

        InputTriggerConfig triggerConfig = BigDataViewer.getInputTriggerConfig(ViewerOptions.options());
        bdvHandle.getKeybindings().addInputMap("bdv ia", createInputMap(triggerConfig));

//        ActionMap actionMap = new ActionMap();
//        actionMap.put("warp section", new AbstractAction() {
//            @Override
//            public void actionPerformed(ActionEvent e) {
//                register();
//            }
//        });
//        bdvHandle.getKeybindings().addActionMap("warp action", actionMap);

        bdvHandle.getKeybindings().addActionMap("bdv ia", createActionMap(this));

        // Configure the UI window
        window.add(bdvHandle.getViewerPanel(), BorderLayout.CENTER);
        window.setBounds(50, 50, 512, 512);
        window.setVisible(true);

        // Add the reference volume
        List<BdvStackSource<?>> ref = BdvFunctions.show(refVol.getHdf5(),
                Bdv.options().addTo(bdvHandle));
        ref.get(0).setColor(new ARGBType(0x00FF00));

//        ref.get(0).setDisplayRange(0, 400);

        // Add the section volume
        ViewId id = new ViewId(0, 0);
        AffineTransform3D transform = refVol.getHdf5().getViewRegistrations().getViewRegistration(id).getModel();
        final BdvSource sec = BdvFunctions.show(secImg,
                "New Section",
                Bdv.options().addTo(bdvHandle).sourceTransform(transform));
        sec.setColor(new ARGBType(0x0000FF));
        sec.setActive(true);
    }

    private InputMap createInputMap(final KeyStrokeAdder.Factory keyProperties) {
        final InputMap inputMap = new InputMap();
        final KeyStrokeAdder map = keyProperties.keyStrokeAdder( inputMap );

        map.put("warp section", "W");
        map.put("help", "F1", "H");

        return inputMap;
    }

    private ActionMap createActionMap(InteractiveAlignmentUi ui) {
        final ActionMap actionMap = new ActionMap();

        new WarpAction("warp section", ui).put(actionMap);
        new ToggleDialogAction("help", helpDialog).put(actionMap);

        return actionMap;
    }


    public class WarpAction extends AbstractNamedAction {

        private final InteractiveAlignmentUi ui;

        public WarpAction(String name, InteractiveAlignmentUi ui) {
            super(name);
            this.ui = ui;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            this.ui.register();
        }
    }


    private <U extends RealType<U> & NativeType<U>> ImagePlus warp(Img<U> source, Img<U> target) throws Exception {
        long[] dim = new long[source.numDimensions()];
        source.dimensions(dim);
        int width = (int) dim[0];
        int height = (int) dim[1];

        RandomAccessibleInterval<BitType> secMsk = SectionImageTool.createMask(source, ops);
        RandomAccessibleInterval<BitType> refMsk = SectionImageTool.createMask(target, ops);

        RandomAccessibleInterval<BitType> secOut = ops.morphology().outline(secMsk, false);
        RandomAccessibleInterval<BitType> refOut = ops.morphology().outline(refMsk, false);

        SectionImageOutlineSampler secCon = new SectionImageOutlineSampler(secOut, triangulationLevels);
        secCon.generatePoints();
        SectionImageOutlineSampler refCon = new SectionImageOutlineSampler(refOut, triangulationLevels);
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

        CoordinateTransformMesh mltMesh = new CoordinateTransformMesh(mlt, resolutionOutput, width, height);
        final TransformMeshMapping<CoordinateTransformMesh> mltMapping = new TransformMeshMapping<>(mltMesh);

        final ImagePlus secImp = ImageJFunctions.wrap(source, "section");
        final ImageProcessor srcIp, trgIp;
        srcIp = secImp.getProcessor();
        trgIp = srcIp.createProcessor(width, height);

        if (interpolate) {
            mltMapping.mapInterpolated(srcIp, trgIp);
        } else {
            mltMapping.map(srcIp, trgIp);
        }

        ImagePlus warp = new ImagePlus("mapped section", trgIp);

        return warp;
    }

    private <M extends RealType<M> & NativeType<M>> void visualiseWarp(Img<M> source, ImagePlus warp, Img<M> target) {
        long[] dim = new long[source.numDimensions()];
        source.dimensions(dim);

        M miSrc = source.firstElement().createVariable();
        miSrc.setReal(0.0);
        M maSrc = ops.stats().max(source);
        M maSrcT = source.firstElement().createVariable();
        maSrcT.setReal(255.0);
        Img<UnsignedByteType> nSrc = ops.convert().uint8(ops.image().normalize(source, miSrc, maSrc, miSrc, maSrcT));

        M miTar = target.firstElement().createVariable();
        miTar.setReal(0.0);
        M maTar = ops.stats().max(target);
        M maTarT = target.firstElement().createVariable();
        maTarT.setReal(255.0);
        Img<UnsignedByteType> nTar = ops.convert().uint8(ops.image().normalize(target, miTar, maTar, miTar, maTarT));

        final ImagePlus secImp = ImageJFunctions.wrap(nSrc, "source");
        final ImagePlus refImp = ImageJFunctions.wrap(nTar, "target");


        ImageStack stk = new ImageStack(secImp.getWidth(), secImp.getHeight(), 3);
        stk.setProcessor(secImp.getProcessor(), 1);
        stk.setProcessor(warp.getProcessor(), 2);
        stk.setProcessor(refImp.getProcessor(), 3);

        CompositeImage res = new CompositeImage(new ImagePlus("warp", stk), CompositeImage.COMPOSITE);
        res.show();
    }

    private void register() {
        Img<UnsignedShortType> refImg = getCurrentSection(0, bdvHandle);//ImgView.wrap(refSec, new ArrayImgFactory<>());
        Img<UnsignedShortType> secImg = getCurrentSection(1, bdvHandle);//ImgView.wrap(newSec, new ArrayImgFactory<>());
//        ImageJFunctions.show(refImg);
//            SectionImageAlignment<UnsignedShortType> alignment = new SectionImageAlignment<>(secImg.copy(), refImg.copy(), 64, true, 5, ops);
        try {
            ImagePlus mapped = warp(secImg, refImg);
            visualiseWarp(secImg, mapped, refImg);
        } catch (Exception e1) {
            e1.printStackTrace();
        }

        System.out.println("was here");
    }

    private static Img<UnsignedShortType> getCurrentSection(int sourceNumber, BdvHandlePanel bdvHandle) {
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

        RealRandomAccessible<UnsignedShortType> interpolated = Views.interpolate(Views.extendZero(rai), new NLinearInterpolatorFactory<>());
        RealRandomAccessible<UnsignedShortType> transformed = RealViews.transform(interpolated, T_vl);
//        ImageJFunctions.show(Views.interval(Views.raster(transformed), rai));
        RandomAccessibleInterval<UnsignedShortType> section = Views.interval(Views.raster(transformed), lb, ub);

        return ImgView.wrap(Views.hyperSlice(section, 2, 0), new ArrayImgFactory<>());
    }

    private List<long[]> getBounds(RandomAccessibleInterval rai, InvertibleRealTransformSequence transform) {
        int n = rai.numDimensions();
        double[] ulc = new double[]{0, 0, 0};
        double[] lrc = new double[]{0L, rai.dimension(1) - 1, rai.dimension(2) - 1};
        double[] lb = new double[n];
        double[] ub = new double[n];
        transform.apply(ulc, lb);
        transform.apply(lrc, ub);

        long[] lbl = new long[n];
        long[] ubl = new long[n];
        for (int i = 0; i < n; i++) {
            lbl[i] = (long) lb[i];
            ubl[i] = (long) ub[i];
        }

//            lbl[n - 1] = 0;
//            ubl[n - 1] = 0;

        List<long[]> bounds = new ArrayList<>(2);
        bounds.add(lbl);
        bounds.add(ubl);

        return bounds;
    }


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

//        Views.
//        final ImgPlus refImgp = new ImgPlus((Img) per, "avg. template");

        AllenRefVol refVol = new AllenRefVol(new File(refPath));
        Dimensions refDims = refVol.getHdf5().getSequenceDescription().getViewSetups().get(0).getSize();
        long xMax = refDims.dimension(0) - 1;

//        for (int d = 0; d < refDims.numDimensions(); d++) {
//            System.out.println(refDims.dimension(d));
//        }
//        System.out.println(refDims.toString());


//        ImagePlus imp = IJ.openImage(secPath);
        RandomAccessibleInterval rai2 = (RandomAccessibleInterval) ij.io().open(secPath);//ImageJFunctions.wrap(imp);
//        ExtendedRandomAccessibleInterval ext = Views.extendZero(rai2);
        RandomAccessibleInterval secVol = Views.addDimension(rai2, 0, xMax);
        RandomAccessibleInterval secVolPer = Views.permute(secVol, 0, 2);
//        RandomAccessibleInterval raie = Views.interval()
//        final ImgPlus secImgp = new ImgPlus((Img)rai, "new section");


//        ij.command().run(InteractiveAlignmentUi.class, true);
        InteractiveAlignmentUi ui = new InteractiveAlignmentUi(secVolPer, refVol, ij.op());
        ui.createAndShow();
    }
}
