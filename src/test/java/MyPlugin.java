import img.AraImgPlus;
import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.ops.OpService;
import net.imglib2.img.Img;

import org.scijava.Initializable;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import rest.Atlas;

import java.io.IOException;

/**
 * @author Felix Meyenhofer
 */
@Plugin(type = Command.class, menuPath = "Plugins > Just a Test")
public class MyPlugin implements Command, Initializable {

    @Parameter
    private OpService ops;


    @Parameter
    ImgPlus imgIn;

//    @Parameter(type = ItemIO.OUTPUT)
//    ImgPlus imgOu;


    @Override
    public void initialize() {
        if (imgIn instanceof AraImgPlus) {
            System.out.println("ok");
        }
    }

    @Override
    public void run() {

//        RandomAccessibleInterval<DoubleType> imgSrc = ops.convert().float64(img);

        if (imgIn instanceof AraImgPlus) {
            AraImgPlus sim = (AraImgPlus) imgIn;
            System.out.println(sim.getPlaneOfSection().toString());
        }


//        AffineTransform2D t = new AffineTransform2D();
//        t.rotate(0.5);
//
//        RealRandomAccessible<DoubleType> interp = Views.interpolate(Views.extendZero(imgSrc), new NLinearInterpolatorFactory());
//        RealRandomAccessible<DoubleType> transf = RealViews.transform(interp, t);
//        RandomAccessibleInterval<DoubleType> raster = Views.interval(Views.raster(transf), img);
//
//
//        IterableInterval<DoubleType> iterable = Views.iterable(raster);
//        DoubleType miSrc = new DoubleType(ops.stats().min(iterable).getRealFloat());
//        DoubleType maSrc = new DoubleType(ops.stats().max(iterable).getRealFloat());
//        DoubleType miTar = new DoubleType(ops.stats().min(img).getRealFloat());
//        DoubleType maTar = new DoubleType(ops.stats().max(img).getRealFloat());
//
//        Img img = ops.convert().uint8(ops.image().normalize(iterable, miSrc, maSrc, miTar, maTar));
//
//        imgOu = new ImgPlus(img);

    }

    public static void main(String[] args) throws IOException {
        ImageJ ij = new ImageJ();
        ij.ui().showUI();

        Img input = (Img) ij.io().open("/Users/turf/Desktop/new section.tif");
//        ImagePlus imp = new ImagePlus("http://imagej.nih.gov/ij/images/blobs.gif");
//        imp.show();

        ImgPlus imgp = new ImgPlus(input);

        AraImgPlus sim = new AraImgPlus(imgp, 1, Atlas.PlaneOfSection.CORONAL, Atlas.VoxelResolution.TWENTYFIVE);
        ij.ui().show(sim);

        ij.command().run(MyPlugin.class, true);
    }
}
