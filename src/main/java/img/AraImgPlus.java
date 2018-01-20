package img;

import net.imagej.ImgPlus;

import net.imglib2.img.Img;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.InvertibleRealTransformSequence;
import net.imglib2.type.numeric.RealType;

/**
 * Allen Reference Atlas (ARA) ImgPlus.
 * holds additionally a mapping that allows to map this image
 * onto the ARA or, conversely, map any data from the ARA onto this image.
 *
 * @author Felix Meyenhofer
 */
public class AraImgPlus<T extends RealType<T>> extends ImgPlus {

    InvertibleRealTransform mapping;


    public AraImgPlus(Img img, InvertibleRealTransform t) {
        super(img);
        this.mapping = t;
    }

    public static void main(String[] args) {
        InvertibleRealTransformSequence sequence = new InvertibleRealTransformSequence();

        Img img = null;
        AraImgPlus ara = new AraImgPlus(img, sequence);
    }
}
