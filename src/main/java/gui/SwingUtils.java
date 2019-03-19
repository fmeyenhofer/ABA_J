package gui;

import java.awt.Frame;
import javax.swing.JFrame;

/**
 * @author Felix Meyenhofer
 */
public class SwingUtils {

    public static Frame grabFrame(String name) {
        for (Frame frame : JFrame.getFrames()) {
            String title = frame.getTitle().replaceAll(" ?[(][0-9]{1,3}%[)]$", "");
            if (title.equals(name)) {
                return frame;
            }
        }
        return null;
    }

//    public static String getZoom(String title) {
//        Pattern pattern = Pattern.compile(".*( ?[(][0-9]{1,3}%[)])$");
//        Matcher matcher = pattern.matcher(title);
//        if (matcher.matches()) {
//            return matcher.group(1);
//        } else {
//            return "";
//        }
//    }
}
