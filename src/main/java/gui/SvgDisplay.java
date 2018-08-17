package gui;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.MalformedURLException;

import javax.swing.*;

import org.apache.batik.swing.JSVGCanvas;
import org.apache.batik.swing.svg.SVGDocumentLoaderAdapter;
import org.apache.batik.swing.svg.SVGDocumentLoaderEvent;
import org.apache.batik.swing.svg.GVTTreeBuilderAdapter;
import org.apache.batik.swing.svg.GVTTreeBuilderEvent;

/**
 * Example code taken from
 * https://xmlgraphics.apache.org/batik/using/swing.html
 * ... and adapted
 * TODO: cleanup the code (docs etc.)
 *
 * @author Felix Meyenhofer
 */
public class SvgDisplay {

    // The frame.
    private JFrame frame;

    // The "Load" button, which displays up a file chooser upon clicking.
    private JButton button = new JButton("Load...");

    // The status label.
    private JLabel label = new JLabel();

    // The SVG canvas.
    private JSVGCanvas svgCanvas = new JSVGCanvas();

    private File source;

    private boolean showTools = false;


    public SvgDisplay() {
    }

    public SvgDisplay(File file) {
        this.source = file;
    }

    public SvgDisplay(boolean toolbar) {
        this.showTools = toolbar;
    }

    public SvgDisplay(File file, boolean toolbar) {
        this(file);
        this.showTools = toolbar;
    }

    public void setSource(File file) {
        this.source = file;
    }

    private JComponent createComponents() {
        // Create a panel and add the button, status label and the SVG canvas.
        final JPanel panel = new JPanel(new BorderLayout());

        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        if (showTools) {
            p.add(button);
        }
        p.add(label);

        panel.add("North", p);
        panel.add("Center", svgCanvas);

        // Set the button action.
        if (showTools) {
            button.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ae) {
                    JFileChooser fc = new JFileChooser("~");
                    int choice = fc.showOpenDialog(panel);
                    if (choice == JFileChooser.APPROVE_OPTION) {
                        source = fc.getSelectedFile();
                        try {
                            svgCanvas.setURI(source.toURI().toURL().toString());
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            });
        }


        // Set the JSVGCanvas listeners.
        svgCanvas.addSVGDocumentLoaderListener(new SVGDocumentLoaderAdapter() {
            public void documentLoadingStarted(SVGDocumentLoaderEvent e) {
                label.setText("Document Loading...");
            }
            public void documentLoadingCompleted(SVGDocumentLoaderEvent e) {
                label.setText("Document Loaded.");
            }
        });

        svgCanvas.addGVTTreeBuilderListener(new GVTTreeBuilderAdapter() {
            public void gvtBuildStarted(GVTTreeBuilderEvent e) {
                label.setText("Build Started...");
            }
            public void gvtBuildCompleted(GVTTreeBuilderEvent e) {
                label.setText("Build Done.");
                frame.pack();
            }
        });

//        svgCanvas.addGVTTreeRendererListener(new GVTTreeRendererAdapter() {
//            public void gvtRenderingPrepare(GVTTreeRendererEvent e) {
//                label.setText("Rendering Started...");
//            }
//            public void gvtRenderingCompleted(GVTTreeRendererEvent e) {
//                label.setText("");
//            }
//        });

        return panel;
    }


    public void show() throws MalformedURLException {
        show(true);
    }

    public void show(boolean exitOnClose) {
        // Create a new JFrame.
        frame = new JFrame("Batik");

        // Add components to the frame.
        frame.getContentPane().add(createComponents());

        // Display the frame.
        if (exitOnClose) {
            frame.addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) {
                    System.exit(0);
                }
            });
        }
        frame.setSize(400, 400);
        frame.setVisible(true);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                try {
                    if (source != null) {
                        svgCanvas.setURI(source.toURI().toURL().toString());
                    }
                } catch (MalformedURLException e1) {
                    e1.printStackTrace();
                }
            }
        });
    }

    public static void main(String[] args) throws MalformedURLException {
        SvgDisplay display = new SvgDisplay();
        display.show();
    }

}
