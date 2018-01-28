package gui;

import java.awt.Frame;

import javax.swing.JFrame;

/**
 * @author Felix Meyenhofer
 */
public class SwingUtils {

    public static Frame grabFrame(String title) {
        for (Frame frame : JFrame.getFrames()) {
            if (frame.getTitle().equals(title)) {
                return frame;
            }
        }
        return null;
    }
}
