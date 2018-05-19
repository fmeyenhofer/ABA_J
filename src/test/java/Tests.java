import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.AffineTransform3D;

/**
 * @author Felix Meyenhofer
 */
public class Tests {
    public static void main(String[] args) {
//        AffineTransform3D t = new AffineTransform3D();
        AffineTransform2D t = new AffineTransform2D();

//        t.scale(1.25);
        t.translate(5,5);
        AffineTransform2D ti = t.copy().inverse();



        double[] p1 = new double[]{40.0, 50.0};
        printArray(p1);

        double[] q1 = new double[2];
        double[] q2 = new double[2];
        double[] q3 = new double[2];

        t.apply(p1, q1);
        printArray(q1);

        t.applyInverse(p1, q2);
        printArray(p1);
        

        ti.apply(p1, q3);
        printArray(q3);
    }

    private static void printArray(double[] a) {
        StringBuilder str = new StringBuilder();
        for (double v : a) {
            str.append(" ").append(v);
        }

        System.out.println(str);
    }
}
