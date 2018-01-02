package gui.tree;

import javax.swing.*;
import java.awt.*;

/**
 * Plain color icon with a black border
 *
 * @author Felix Meyenhofer
 */
public class MonoChromaticIcon implements Icon {

    private int width;
    private int height;
    private Color fillColor;
    private Color borderColor = new Color(140, 140, 140); // same as expansion triangles
    private int borderThickness;

    private MonoChromaticIcon() {
        this.width = 16;
        this.height = 16;
        this.borderThickness = 1;
    }

    MonoChromaticIcon(Color color) {
        this();
        this.fillColor = color;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        // Draw border
        g.setColor(this.borderColor);
        g.drawRect(x, y, this.width, this.height);

        // Draw the rectangle fill
        x++;
        y++;
        int iw = this.width - 2 * this.borderThickness + 1;
        int ih = this.height - 2 * this.borderThickness + 1;
        g.setColor(this.fillColor);
        g.fillRect(x, y, iw, ih);
    }

    @Override
    public int getIconWidth() {
        return this.width;
    }

    @Override
    public int getIconHeight() {
        return this.height;
    }
}
