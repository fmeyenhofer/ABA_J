import img.SectionImageOutlineSampler;
import img.SectionImageTool;

import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.ops.OpService;

import net.imglib2.Interval;
import net.imglib2.FinalInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.RandomAccessibleInterval;
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

import org.apache.commons.lang.ArrayUtils;

import org.nd4j.linalg.api.ndarray.INDArray;

import org.scijava.ItemIO;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import java.io.IOException;

/**
 * @author Felix Meyenhofer
 */
@Plugin(type = Command.class, menuPath = "Plugins > Allen Brain Atlas > Pre-Processing > Orient + Auto-Crop")
public class SectionImagePreprocessing<T extends RealType<T> & NativeType<T>> implements Command {

    @Parameter
    private OpService ops;

    @Parameter
    private UIService uis;

    @Parameter
    private StatusService status;


    @Parameter(label = "Input section")
    private ImgPlus<T> inputSection;

    @Parameter(label = "Crop margin")
    private long margin = 50;

    @Parameter(label = "Show mask")
    private boolean showMask = false;

    @SuppressWarnings("unused")
    @Parameter(type = ItemIO.OUTPUT)
    private ImgPlus outputSection;


    @Override
    public void run() {
        status.showStatus(0, 100, "process input");

        RandomAccessibleInterval<DoubleType> imgSrc = ops.convert().float64(inputSection);

        double factor = 300.0 / (double) inputSection.dimension(0);
        double[] scaleFactors = new double[inputSection.numDimensions()];
        for (int d = 0; d < inputSection.numDimensions(); d++) {
            scaleFactors[d] = factor;
        }

        status.showStatus(5, 100, "process input");
        RandomAccessibleInterval<DoubleType> sca = ops.transform().scaleView(imgSrc, scaleFactors, new NLinearInterpolatorFactory<>());

        status.showStatus(10, 100,"create section mask");
        Img<BitType> msk = SectionImageTool.createMask(ImgView.wrap(sca, new ArrayImgFactory<>()), ops);

        if (showMask) {
            uis.show(msk);
        }

        status.showStatus(30, 100,"create mask outline");
        RandomAccessibleInterval<BitType> out = ops.morphology().outline(msk, false);

        status.showStatus(40, 100, "contour analysis");
        SectionImageOutlineSampler sampler = new SectionImageOutlineSampler(out, 4);
        sampler.doPca();
        INDArray outlineCoordinates = sampler.getRotatedCoordinates();
        INDArray minima = outlineCoordinates.min(0);
        INDArray maxima = outlineCoordinates.max(0);
        INDArray minimat = minima.mul(1 / factor);
        INDArray maximat = maxima.mul(1 / factor);

        long[] ulct = new long[]{minimat.getInt(0) - margin, minimat.getInt(1) - margin};
        long[] lrct = new long[]{maximat.getInt(0) + margin, maximat.getInt(1) + margin};

        System.out.println("ulc and lrc transformed");
        System.out.println(ArrayUtils.toString(ulct));
        System.out.println(ArrayUtils.toString(lrct));
        Interval boundingBox = new FinalInterval(ulct, lrct);

        status.showStatus(50,100, "rotate");
        AffineTransform2D t = new AffineTransform2D();
        t.rotate(sampler.getRotation());

        double[] mint = new double[2];
        double[] maxt = new double[2];
        t.apply(new double[]{0, 0}, mint);
        t.apply(new double[]{(double) imgSrc.max(0), (double) imgSrc.max(1)}, maxt);
        Interval interval = new FinalInterval(
                new long[]{(long) mint[0], (long) mint[1]},
                new long[]{(long) maxt[0], (long) maxt[1]});

        RealRandomAccessible<DoubleType> interp = Views.interpolate(Views.extendZero(imgSrc), new NLinearInterpolatorFactory());
        RealRandomAccessible<DoubleType> transf = RealViews.transform(interp, t);
        RandomAccessibleInterval<DoubleType> raster = Views.interval(Views.raster(transf), interval);

        status.showStatus(60,100, "crop");
        RandomAccessibleInterval crop = ops.transform().crop(raster, boundingBox);
//        RandomAccessibleInterval crop = Views.interval(Views.extendZero(raster), boundingBox); // This does not work for some reason

        status.showStatus(80, 100, "intensity scaling");
        Img<T> imgTar = SectionImageTool.double2Whatever(crop, inputSection, ops);

        outputSection = new ImgPlus(imgTar, inputSection);

        status.showStatus(100,100, "done");
    }


    public static void main(String[] args) throws IOException {
        ImageJ ij = new ImageJ();
        ij.ui().showUI();

//        Object img = ij.io().open("/Users/turf/switchdrive/SJMCS/data/lamy-lab/floating/160128_crym_gng2/ome/series-2/crym(cy3)_gng2(A488)_IHC(150914)_DGC4_1 - 2016-01-28 05.03.56-Cy3_ROI-09.ome.tif");
        Object img = ij.io().open("/Users/turf/switchdrive/SJMCS/data/lamy-lab/floating/160128_crym_gng2/ome/series-3/crym(cy3)_gng2(A488)_IHC(150914)_DGC4_1 - 2016-01-28 05.03.56-FITC_ROI-09.ome.tif");
        ij.ui().show(img);

        ij.command().run(SectionImagePreprocessing.class, true);
    }
}

