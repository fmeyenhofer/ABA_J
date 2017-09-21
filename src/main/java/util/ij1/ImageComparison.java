package util.ij1;

import ij.process.ImageProcessor;
import mpicbg.imagefeatures.FloatArray2D;
import mpicbg.imagefeatures.ImageArrayConverter;

/**
 * @author Felix Meyenhofer
 */
public class ImageComparison {

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
