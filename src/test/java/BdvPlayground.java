import bdv.BigDataViewer;
import bdv.tools.HelpDialog;
import bdv.tools.ToggleDialogAction;
import bdv.util.*;
import bdv.viewer.ViewerOptions;
import bdv.viewer.state.SourceState;
import bdv.viewer.state.ViewerState;
import ij.ImagePlus;
import net.imagej.ImageJ;
import net.imagej.ops.OpService;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.img.ImgView;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InvertibleRealTransformSequence;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.Views;
import org.scijava.ui.behaviour.KeyStrokeAdder;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.AbstractNamedAction;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;

/**
 * @author Felix Meyenhofer
 */
public class BdvPlayground {

    private BdvSource ref;
    private final BdvSource sec;
    private final BdvHandlePanel bdvHandle;
    private final RandomAccessibleInterval<UnsignedByteType> refVol;

    private BdvPlayground() {
        ImagePlus vol = new ImagePlus("http://imagej.nih.gov/ij/images/flybrain.zip");
        RandomAccessibleInterval<ARGBType> img = ImageJFunctions.wrap(vol);
        Converter<ARGBType, UnsignedByteType> converter = new Converter<ARGBType, UnsignedByteType>() {
            @Override
            public void convert(ARGBType in, UnsignedByteType out) {
                int color = in.get();
                out.set((ARGBType.red(color) + ARGBType.green(color) + ARGBType.blue(color))/ 3);
            }
        };
        refVol = Converters.convert(img, converter, new UnsignedByteType());


        // Setup BDV
        JFrame window = new JFrame("Interactive Section Alignment");

        BdvOptions options = Bdv.options();
        options.axisOrder(AxisOrder.XYZ);
        bdvHandle = new BdvHandlePanel(window, options);

        window.add(bdvHandle.getViewerPanel(), BorderLayout.CENTER);
        window.setBounds(50, 50, 512, 512);
        window.setVisible(true);

        // Add the help window and trigger for the warp() callback
        final InputMap inputMap = new InputMap();
        InputTriggerConfig triggerConfig = BigDataViewer.getInputTriggerConfig(ViewerOptions.options());
        KeyStrokeAdder adder = triggerConfig.keyStrokeAdder(inputMap);
        adder.put("help", "F1", "H");
        adder.put("warp section", "W");
        final ActionMap actionMap = new ActionMap();
        new ToggleDialogAction("help", new HelpDialog(window, BigDataViewer.class.getResource("/viewer/Help.html"))).put(actionMap);
        new WarpAction("warp section", this).put(actionMap);
        bdvHandle.getKeybindings().addInputMap("bdv", inputMap);
        bdvHandle.getKeybindings().addActionMap("bdv", actionMap);


        ref = BdvFunctions.show(refVol, "ref. vol.", BdvOptions.options().addTo(bdvHandle));
        ref.setColor(new ARGBType(0x00FF00));
        ref.setActive(true);

        sec = BdvFunctions.show(Views.hyperSlice(refVol, 2, refVol.max(2)/2), "section", Bdv.options().is2D().addTo(bdvHandle));
        sec.setColor(new ARGBType(0x0000FF));
    }

    public static void main(String[] args) throws IOException {
//        String secPath = "/Users/turf/switchdrive/SJMCS/data/devel/small-deformations/crym(cy3)_gng2(A488)_IHC(150914)_DGC4_1 - 2016-01-28 05.03.56-FITC_ROI-00.tif";
//        String refPath = "/Users/turf/switchdrive/SJMCS/data/devel/small-deformations/average_template_25um_coronal-300_tps1.tif";

//        String secPath =
//        String refPath = "/Users/turf/allen-cache/reference-volumes/average_template_25.nrrd";

        ImageJ ij = new ImageJ();
//        ij.ui().showUI();



//        Img secImg = (Img) ij.io().open(secPath);
//        Img refVol = (Img) ij.io().open("http://imagej.nih.gov/ij/images/flybrain.zip");

//        Nrrd_Reader reader = new Nrrd_Reader();
//        ImagePlus imp = reader.load(new File(refPath).getParent(), new File(refPath).getName());


        new BdvPlayground();

//        ref.setDisplayRange(0, 400);

//        RandomAccessibleInterval secVol =  Views.permute(Views.addDimension(secImg, 0, refVol.max(0)), 0, 2);
//        BdvSource sec = BdvFunctions.show(secVol, "sec. vol.", BdvOptions.options().addTo(bdvHandle));
//        sec.setColor(new ARGBType(0x0000FF));


//        Img<UnsignedShortType> sec1 = getSection(0, bdvHandle);
//        Img<UnsignedShortType> sec2 = getSection(1, bdvHandle);

//        Img<BitType> msk1 = ij.op().create().img(sec1, new BitType());
//        ij.op().threshold().huang(msk1, sec1);

//        Img<UnsignedShortType> fil = sec1.copy();
//        ij.op().filter().gauss(fil, 9);

//        Img<BitType> msk11 = subroutine(sec1, ij.op());


//        ImageJFunctions.show(sec1, "src 1");
//        ImageJFunctions.show(sec2, "src 2");
//        ImageJFunctions.show(msk1, "msk 1");
//        ImageJFunctions.show(msk11, "msk 11");
//        ImageJFunctions.show(msk2, "msk 2");
//        ImageJFunctions.show(fil, "fil");

        System.out.println("main: done");

    }

    private static Img<UnsignedShortType> getSection(int sourceNumber, BdvHandlePanel bdvHandle) {
        ViewerState viewerState = bdvHandle.getViewerPanel().getState();

        int yMax = bdvHandle.getViewerPanel().getDisplay().getHeight();
        int xMax = bdvHandle.getViewerPanel().getDisplay().getWidth();
        long[] lb = new long[]{0, 0, 0};
        long[] ub = new long[]{xMax, yMax, 0};

        final AffineTransform3D T_vw = new AffineTransform3D();
        bdvHandle.getViewerPanel().getState().getViewerTransform(T_vw);

        SourceState srcState = bdvHandle.getViewerPanel().getState().getSources().get(sourceNumber);
        final AffineTransform3D T_wl = new AffineTransform3D();
        srcState.getSpimSource().getSourceTransform(0, 0, T_wl);

        final InvertibleRealTransformSequence T_vl = new InvertibleRealTransformSequence();
        T_vl.add(T_wl);
        T_vl.add(T_vw);

        SourceState refState = viewerState.getSources().get(0);
        RandomAccessibleInterval<UnsignedShortType> rai = refState.getSpimSource().getSource(0, 0);
        RealRandomAccessible<UnsignedShortType> interpolated = Views.interpolate(Views.extendZero(rai), new NLinearInterpolatorFactory<>());
        RealRandomAccessible<UnsignedShortType> transformed = RealViews.transform(interpolated, T_vw);

        RandomAccessibleInterval<UnsignedShortType> refSec = Views.interval(Views.raster(transformed), lb, ub);

        return ImgView.wrap(refSec, new ArrayImgFactory<>());
    }

    private static <T extends RealType<T>> Img<BitType> subroutine(Img<T> img, OpService opService) {
        Img<BitType> msk = opService.create().img(img, new BitType());
        opService.threshold().huang(msk, img);

        return msk;
    }

    static void warp(BdvPlayground ui) {
        ViewerState viewerState = ui.bdvHandle.getViewerPanel().getState();
        SourceState srcState = viewerState.getSources().get(0);
        AffineTransform3D transform = new AffineTransform3D();
        srcState.getSpimSource().getSourceTransform(0,0, transform);
        transform.rotate(1, 1.5);

        ui.ref.removeFromBdv();
        ui.ref = BdvFunctions.show(ui.refVol, "ui.ref. vol.",
                BdvOptions.options().addTo(ui.bdvHandle).sourceTransform(transform));
        ui.ref.setColor(new ARGBType(0x00FF00));
        ui.ref.setActive(true);

        ui.bdvHandle.getViewerPanel().requestRepaint();
        ui.bdvHandle.getViewerPanel().showMessage("warp");
//        System.out.println("warp callback.");
    }



    public static class WarpAction extends AbstractNamedAction {

        private final BdvPlayground ui;

        WarpAction(String name, BdvPlayground bdvHandle) {
            super(name);
            this.ui = bdvHandle;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
//            this.ui.register();
            warp(this.ui);
        }
    }



}
