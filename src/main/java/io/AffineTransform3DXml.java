package io;

import net.imglib2.realtransform.AffineTransform3D;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;

/**
 * @author Felix Meyenhofer
 */
public class AffineTransform3DXml {
    private File file;
    private Document dom;

    AffineTransform3DXml(AffineTransform3D affine, File file) throws IOException {
        this.file = file;
        this.write(affine, file);
    }

    AffineTransform3DXml(File file) throws IOException, JDOMException {
        this.file = file;
        this.dom = this.read(file);
    }

    private void write(AffineTransform3D affine, File file) throws IOException {
        Element root = new Element("Affine3D");

        for (int row = 0; row <= 3; row++) {
            for (int col = 0; col <= 3; col++) {
                Double value = affine.get(row, col);
                Element element = new Element("m" + row + col);
                element.setText(value.toString());
                root.addContent(element);
            }
        }

        Document document = new Document();
        document.setContent(root);

        XMLOutputter outPutter = new XMLOutputter();
        outPutter.setFormat(Format.getPrettyFormat());
        outPutter.output(dom, new FileWriter(file));
    }

    private Document read(File file) throws IOException, JDOMException {
        StringBuilder str = new StringBuilder();
        Scanner scanner = new Scanner(file);
        while (scanner.hasNext()) {
            str.append(scanner.nextLine());
        }
        scanner.close();

        SAXBuilder builder = new SAXBuilder();

        return builder.build(new ByteArrayInputStream(str.toString().getBytes()));
    }

    AffineTransform3D getAffineTransform3D() {
        double[] m = new double[16];
        int i = 0;
        for (Element child : this.dom.getRootElement().getChildren()) {
            m[i++] = Double.parseDouble(child.getText());
        }

        AffineTransform3D affine = new AffineTransform3D();
        affine.set(m);

        return affine;
    }

}
