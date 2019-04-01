package img;

import ij.ImagePlus;
import ij.process.ImageProcessor;

import mpicbg.imagefeatures.FloatArray2D;
import mpicbg.imagefeatures.ImageArrayConverter;

/**
 * Utility functions to deal with ij1 data structures
 * {@link ImagePlus} and {@link ImageProcessor}
 *
 * @author Felix Meyenhofer
 */
public class ImagePlusUtils {

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

//    public static int getMaskArea(ImageProcessor processor) {
//        int[] hist = processor.getStats().histogram;
//
//        return hist[hist.length - 1];
//    }

    public static float meanSquareDifference(ImageProcessor pro1, ImageProcessor pro2) {
        if ((pro1.getHeight() != pro2.getHeight()) || (pro1.getHeight() != pro2.getHeight())) {
            throw new RuntimeException("Image dimensions must be identical");
        }

        FloatArray2D fa1 = new FloatArray2D(pro1.getWidth(), pro1.getHeight());
        FloatArray2D fa2 = new FloatArray2D(pro2.getWidth(), pro2.getHeight());

        ImageArrayConverter.imageProcessorToFloatArray2DCropAndNormalize(pro1, fa1);
        ImageArrayConverter.imageProcessorToFloatArray2DCropAndNormalize(pro2, fa2);

        int N = fa1.data.length;

        float msd = 0;
        float diff;
        for (int i = 0; i < N; i++) {
            diff = fa1.data[i] - fa2.data[i];
            msd += diff * diff;
        }

        return msd;
    }
}
