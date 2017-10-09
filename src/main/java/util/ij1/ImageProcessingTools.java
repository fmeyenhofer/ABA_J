package util.ij1;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import net.imagej.ImageJ;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;

import java.io.IOException;

/**
 * @author Felix Meyenhofer
 */
public class ImageProcessingTools {
    

    public static void adjustContrast(ImageProcessor processor, double saturationFraction) {

        if (0.0 > saturationFraction || saturationFraction > 1.0) {
            throw new RuntimeException("The saturation fraction has to be in the interval [0...1].");
        }

        int maxPixel = (int) (processor.getPixelCount() * saturationFraction);
        int[] hist = processor.getHistogram();

        int lowerBound = 0;
        while (hist[lowerBound] == 0) {
            lowerBound++;
        }

        int upperBound = 0;
        int nPixel = 0;
        while (nPixel < maxPixel) {
            nPixel += hist[upperBound++];
        }

//        System.out.println("min = " + lowerBound + ", max = " + upperBound);

        processor.setMinAndMax((double) lowerBound, (double) upperBound);
    }

    public static int getMaskArea(ImageProcessor processor) {
        int[] hist = processor.getStats().histogram;
        return hist[hist.length - 1];
    }


    public static void main(String[] args) throws IOException {
        final ImageJ ij = new net.imagej.ImageJ();
        ij.ui().showUI();

        Object img = ij.io().open("/Users/turf/switchdrive/SJMCS/data/devel/section2volume/average_template_25um_coronal_295-315.tif");

        ImagePlus imp = ImageJFunctions.wrap((RandomAccessibleInterval) img, "test");
        imp.show();

        ImageProcessingTools.adjustContrast(imp.getProcessor(), 0.99);

        imp.updateAndRepaintWindow();
    }
}
