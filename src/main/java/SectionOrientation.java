import img.SectionImageOutline;
import img.SectionImageTool;

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.ops.OpService;
import net.imglib2.*;
import net.imglib2.img.Img;
import net.imglib2.img.ImgView;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.Views;
import org.scijava.ItemIO;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.IOException;

/**
 * @author Felix Meyenhofer
 */
@Plugin(type = Command.class, menuPath = " Plugins > Allen Brain Atlas > 1. Pre-Processing > Orient")
public class SectionOrientation <T extends RealType<T> & NativeType<T>> implements Command {
    @Parameter
    private OpService ops;

    @Parameter
    private StatusService status;


    @Parameter(label = "Input Section")
    private ImgPlus<T> inputSection;

    @SuppressWarnings("unused")
    @Parameter(type = ItemIO.OUTPUT)
    private ImgPlus outputSection;


    @Override
    public void run() {
        status.showStatus(0, 100, "process input");

        RandomAccessibleInterval<DoubleType> imgSrc = ops.convert().float64(inputSection);

        double factor = 300.0 / (double) imgSrc.dimension(0);
        double[] scaleFactors = new double[imgSrc.numDimensions()];
        for (int d = 0; d < imgSrc.numDimensions(); d++) {
            scaleFactors[d] = factor;
        }

        RandomAccessibleInterval<DoubleType> sca = ops.transform().scaleView(imgSrc, scaleFactors, new NLinearInterpolatorFactory<>());
//        ImageJFunctions.show(sca);

        status.showStatus(10, 100,"create section mask");
        Img<BitType> msk = SectionImageTool.createMask(ImgView.wrap(sca, new ArrayImgFactory<>()), ops);
//        ImageJFunctions.show(msk);

        status.showStatus(30, 100,"create mask outline");
        RandomAccessibleInterval<BitType> out = ops.morphology().outline(msk, false);

        status.showStatus(40, 100, "contour analysis");
        SectionImageOutline sampler = new SectionImageOutline(out, 4);
        sampler.doPca();

        AffineTransform2D t = new AffineTransform2D();
        t.rotate(sampler.getRotation());
        System.out.println(sampler.getRotation());

        status.showStatus(50,100, "rotate");
        RealRandomAccessible<DoubleType> interp = Views.interpolate(Views.extendZero(imgSrc), new NLinearInterpolatorFactory());
        RealRandomAccessible<DoubleType> transf = RealViews.transform(interp, t);

        double[] mint = new double[2];
        double[] maxt = new double[2];
        t.apply(new double[]{0, 0}, mint);
        t.apply(new double[]{(double) imgSrc.max(0), (double) imgSrc.max(1)}, maxt);
        Interval interval = new FinalInterval(
                new long[]{(long) mint[0], (long) mint[1]},
                new long[]{(long) maxt[0], (long) maxt[1]});

        RandomAccessibleInterval<DoubleType> raster = Views.interval(Views.raster(transf), interval);
        
        Img<T> imgTar = SectionImageTool.double2Whatever(raster, inputSection, ops);
        outputSection = new ImgPlus(ImgView.wrap(imgTar, new ArrayImgFactory<>()), inputSection);

        status.showStatus(100,100, "done");
    }


    public static void main(String[] args) throws IOException {
        ImageJ ij = new ImageJ();
        ij.ui().showUI();

//        Object img = ij.io().open("/Users/turf/switchdrive/SJMCS/data/lamy-lab/floating/160128_crym_gng2/ome/series-2/crym(cy3)_gng2(A488)_IHC(150914)_DGC4_1 - 2016-01-28 05.03.56-Cy3_ROI-09.ome.tif");
        Object img = ij.io().open("/Users/turf/switchdrive/SJMCS/data/lamy-lab/floating/160128_crym_gng2/ome/series-3/crym(cy3)_gng2(A488)_IHC(150914)_DGC4_1 - 2016-01-28 05.03.56-FITC_ROI-09.ome.tif");
        ij.ui().show(img);

        ij.command().run(SectionOrientation.class, true);
    }
}
