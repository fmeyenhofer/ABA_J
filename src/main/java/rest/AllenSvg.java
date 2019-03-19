package rest;

import gui.SvgDisplay;

import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.anim.dom.SVGDOMImplementation;
import org.apache.batik.util.XMLResourceDescriptor;
import org.w3c.dom.*;

import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Class to handle the SVG files downloaded from the Allen API
 *
 * @author Felix Meyenhofer
 */
class AllenSvg extends AllenFile {

    /** SVG content */
    private Document dom;

    /** path attribute to identify the contour */
    private static final String PATH_CONTOUR_ATTRIBUTE = "structure_id";

    /** path attribute value to identify the contour */
    private static final String PATH_CONTOUR_VALUE = "8";


    /**
     * {@inheritDoc}
     */
    AllenSvg(URL url, File file) throws IOException, URISyntaxException, TransformerException {
        super(url, file);
    }

    /**
     * {@inheritDoc}
     */
    AllenSvg(File file) throws IOException, URISyntaxException {
        super(file);

    }

    private Document getDom() {
        return dom;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void load(File file) throws IOException, URISyntaxException {
        load(file.toURI().toString());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void load(URL url) throws IOException {
        load(url.toString());
    }

    /**
     * Loading always works with the URI...
     *
     * @throws IOException
     */
    private void load(String uri) throws IOException {
        String parser = XMLResourceDescriptor.getXMLParserClassName();
        SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(parser);
        this.dom = factory.createDocument(uri);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void save() throws TransformerException, FileNotFoundException {
        Transformer t = TransformerFactory.newInstance().newTransformer();
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        t.setOutputProperty(OutputKeys.METHOD, "xml");
        t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        t.transform(new DOMSource(getDom()), new StreamResult(new FileOutputStream(getFile())));
    }

    /**
     * Get the contour over all the annotations (brain section contours)
     *
     * @return SVG containing the brain section contours
     */
    public Document createGrayMatterSvg(File file) throws TransformerException, FileNotFoundException {
        if (dom == null) {
            throw (new RuntimeException("No document loaded. use the load() method."));
        }

        // Filter
        Map<String, String[]> filters = new HashMap<>(1);
        filters.put("path", new String[]{"structure_id", "8"});
        filters.put("#text", null);
        Node node = selectiveClone(dom.getDocumentElement(), filters);

        // Create a new document
        DOMImplementation impl = SVGDOMImplementation.getDOMImplementation();
        Document doc = impl.createDocument(SVGDOMImplementation.SVG_NAMESPACE_URI, "svg", null);
        Element root = doc.getDocumentElement();

        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item((i));
            doc.adoptNode(child);
            root.appendChild(child);
        }

        // Write to file
        Transformer t = TransformerFactory.newInstance().newTransformer();
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        t.setOutputProperty(OutputKeys.METHOD, "xml");
        t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        t.transform(new DOMSource(doc), new StreamResult(new FileOutputStream(file)));

        return doc;
    }


    /**
     * Find a path element among the children of the {@param node} file with an attribute {@param name} and its
     * {@param value}.
     *
     * @param node SCG node
     * @param name node attribute name
     * @param value node attribute value
     * @return
     */
    private Node findPath(Node node, String name, String value) {
        NodeList children = node.getChildNodes();
        for (int c = 0; c < children.getLength(); c++) {
            Node child = children.item(c);

            if (child.hasChildNodes()) {
                return findPath(child, name, value);
            } else {
                if (child.getNodeName().equals("path")) {
                    if (child.getAttributes().getNamedItem(name).getNodeValue().equals(value)) {
                        return child;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Find a path element among the children of the root element of this SVG file ({@link #dom})
     * using an node attribute {@param name} and its {@param value}.
     *
     * @param name node attribute name
     * @param value node attribute value
     * @return
     */
    private Node findPath(String name, String value) {
        return findPath(dom.getDocumentElement(), name, value);
    }

    /**
     * Clone a node and its children, provided that there is no filter that chucks them out. This method is recursive
     * whenever the node has child nodes.
     *
     * @param node input document element
     * @param filters {@link Map} where the key is a node name, and the values are attribute-value tuples.
     * @return
     */
    private Node selectiveClone(Node node, Map<String, String[]> filters) {
        Node copy = node.cloneNode(false);

        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item((i));

            boolean skip = false;
            for (String filter : filters.keySet()) {
                String[] attributeValue = filters.get(filter);
                if (child.getNodeName().equals(filter)) {
                    if ((attributeValue == null) || !child.getAttributes().getNamedItem(attributeValue[0]).getNodeValue().equals(attributeValue[1])) {
                        skip = true;
                        break;
                    }
                }
            }

            if (!skip) {
                Node copy2;
                if (node.hasChildNodes()) {
                    copy2 = selectiveClone(child, filters);
                } else {
                    copy2 = child.cloneNode(false);
                }
                copy.appendChild(copy2);
            }
        }

        return copy;
    }

    /**
     * Quick functionality testing
     *
     * @param args
     * @throws IOException
     * @throws TransformerException
     * @throws URISyntaxException
     */
    public static void main(String[] args) throws IOException, TransformerException, URISyntaxException {
//        URL query = new URL("http://api.brain-map.org/api/v2/svg_download/100960335?downsample=3");
        File home = new File(System.getProperty("user.home"), "allen-cache");
        URL query = new URL("http://api.brain-map.org/api/v2/svg_download/100960333?downsample=3");
        File file = new File(home, "100960333.svg");

        AllenSvg svg = new AllenSvg(query, file);

        File cfile = new File(home,"100960333_contour.svg");
        svg.createGrayMatterSvg(cfile);

        SvgDisplay disp = new SvgDisplay(true);
        disp.setSource(cfile);
        disp.show();
    }
}
