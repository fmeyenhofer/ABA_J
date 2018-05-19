import net.imagej.ImgPlus;
import net.imglib2.img.Img;

class ExtendedImgPlus extends ImgPlus {

    public ExtendedImgPlus(Img img) {
        super(img);
    }

    public void additionalMethod() {
        System.out.println("this is " + this.getClass().toString());
    }
}
