package rest;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import javax.xml.transform.TransformerException;
import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * @author Felix Meyenhofer
 */
public class AllenXml extends AllenFile {

    /** Xml content */
    private Document dom;

    /**
     * Constructor to instantiate a xml file without
     * saving it.
     *
     * @param url to query the Allen API
     * @throws TransformerException parser error
     * @throws IOException invalid file
     * @throws URISyntaxException invalid URI
     */
    AllenXml(URL url) throws TransformerException, IOException, URISyntaxException {
        super(url, null);
    }

    /**
     * {@inheritDoc}
     */
    AllenXml(URL url, File file) throws TransformerException, IOException, URISyntaxException {
        super(url, file);
    }

    /**
     * {@inheritDoc}
     */
    AllenXml(File file) throws IOException, URISyntaxException {
        super(file);
    }

    /**
     * Constructor to create and save a new xml document from
     * an {@link Element}
     *
     * @param element root xml element
     * @param file xml file
     * @throws TransformerException parser error
     * @throws IOException invalid file
     */
    AllenXml(Element element, File file) throws TransformerException, IOException {
        this.setFile(file);
        this.dom = new Document((Element)element.clone());
        this.save();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void load(File file) throws IOException {
        load(new Scanner(file));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void load(URL url) throws IOException {
        load(new Scanner(url.openStream()));
    }

    /**
     * Load the content
     *
     * @param scanner stream scanner
     * @throws IOException if the connection cannot be established
     */
    private void load(Scanner scanner) throws IOException {
        StringBuilder str = new StringBuilder();
        while (scanner.hasNext()) {
            str.append(scanner.nextLine());
        }
        scanner.close();

        SAXBuilder builder = new SAXBuilder();
        try {
            dom = builder.build(new ByteArrayInputStream(str.toString().getBytes()));
        } catch (JDOMException e) {
            e.printStackTrace();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void save() throws TransformerException, IOException {
        XMLOutputter outputter = new XMLOutputter();
        outputter.setFormat(Format.getPrettyFormat());
        outputter.output(dom, new FileWriter(this.getFile().getAbsoluteFile()));
    }

    private Document getDom() {
        return dom;
    }

    /**
     * print the xml element and all its children
     *
     * @param element xml
     */
    private void printElements(Element element) {
        printElements(element, "");
    }

    /**
     * Recursively print all the elements of the xml document.
     * The lines are indented according the hierarchie.
     *
     * @param element xml element to print
     * @param indent of the line
     */
    private void printElements(Element element, String indent) {
        System.out.println(indent + element.getName() + ": " + element.getContent().toString());
        indent += "\t";
        for (Object obj : element.getChildren()) {
            Element nod = (Element) obj;
            if (nod.getChildren().size() > 0) {
                printElements(nod, indent);
            } else {
                System.out.println(indent + nod.getName() + ": " + nod.getContent().toString());
            }
        }
    }

    /**
     * Get the value of the first encountered tag with a given name
     *
     * @param tag_name name of the tag
     * @return tag value
     */
    public String getValue(String tag_name) {
        return getValue(tag_name, this.dom.getRootElement());
    }

    /**
     * Get the value of the first encountered tag with a given name.
     * The search is recursive for all the children of a node
     *
     * @param tag_name name of the tag
     * @param element xml element that is searched
     * @return tag value
     */
    private String getValue(String tag_name, Element element) {
        if (element.getName().equals(tag_name)) {
            return element.getValue();
        }

        List children = element.getChildren();
        if (children.size() > 0) {
            for (Object obj : children) {
                String value = getValue(tag_name, (Element) obj);
                if (value != null) {
                    return value;
                }
            }
        }

        return null;
    }

    /**
     * get the values of all tags of a given name
     *
     * @param tag_name of the xml tag
     * @return tag content
     */
    public ArrayList<List> getValues(String tag_name) {
        return getValues(tag_name, getDom().getRootElement());
    }

    /**
     * Get the values of all the tags in a given depth in the
     * xml tree.
     *
     * @param tag_name of the xml tag
     * @param max_depth in the xml tree
     * @return collector of the xml elements
     */
    public ArrayList<List> getValues(String tag_name, int max_depth) {
        ArrayList<List> results = new ArrayList<>();
        getValues(tag_name, this.dom.getRootElement(), results, 0, max_depth);
        return results;
    }

    /**
     * get the values of all the tags of a given name in the xml element.
     *
     * @param tag_name of the xml tag
     * @param element xml content
     * @return tag content
     */
    private ArrayList<List> getValues(String tag_name, Element element) {
        ArrayList<List> result = new ArrayList<>();
        getValues(tag_name, element, result);
        return result;
    }

    /**
     * Get the values of all the tags of a given name in the xml element.
     *
     * @param tag_name of the xml tag
     * @param element xml content
     * @param result collector for the xml values
     */
    private void getValues(String tag_name, Element element, ArrayList<List> result) {
        if (element.getName().equals(tag_name)) {
            result.add(element.getContent());
        }

        List children = element.getChildren();
        if (children.size() > 0) {
            for (Object obj : children) {
                getValues(tag_name, (Element) obj, result);
            }
        }
    }

    /**
     * Get all the elements of the queries data model.
     * This is usually the grand-child of the root element:
     *
     * [root]
     *    |- [model]s
     *       |- [model]
     *          |- element 1
     *          |- element 2
     *          |- ...
     *
     * @return list of elements/nodes
     */
    public List<Element> getElements() {
        Element root = dom.getRootElement();
        Element only_child = (Element) root.getChildren().get(0);

        List<Element> elements = new ArrayList<>();
        for (Object obj : only_child.getChildren()) {
            elements.add((Element) obj);
        }

        return elements;
    }

    /**
     * Get the first element with a particular name
     *
     * @param name of the element
     * @return first element with the corresponding name
     */
    public Element getElement(String name) {
        return getElement(name, getDom().getRootElement());
    }

    /**
     * Recursive search for an element.
     *
     * @param name of the element
     * @param element current element
     * @return element of a given name or null if nothing was found
     */
    private Element getElement(String name, Element element) {
        if (element.getName().equals(name)) {
            return element;
        }

        List children = element.getChildren();
        if (children.size() > 0) {
            for (Object obj : children) {
                Element result = getElement(name, (Element) obj);
                if (result != null) {
                    return result;
                }
            }
        }

        return null;
    }

    /**
     * Get the values of all the tags in a given depth in the
     * xml tree.
     *
     * @param tag_name of the element
     * @param element current element
     * @param results all values of the elements of a given tag_name
     * @param depth of recursive search (current)
     * @param max_depth or recursive search
     */
    private void getValues(String tag_name, Element element, ArrayList<List> results, int depth, int max_depth) {
        if (element.getName().equals(tag_name)) {
            results.add(element.getContent());
        }

        if (depth < max_depth) {
            List children = element.getChildren();
            for (Object obj : children) {
                getValues(tag_name, (Element) obj, results, depth++, max_depth);
            }
        }
    }

    /**
     * Retrieve the model name of the response. This is conventionally the
     * second child of the document root (response tag)
     *
     * @return string indicating the name of the data model
     */
    String getResponseModel() {
        Element element = dom.getRootElement();
        List children = element.getChildren();
        if (children.size() != 1) {
            throw new RuntimeException("Allen Brain Atlas API, response XML:" +
                    " expected the root element to have one single child, not " + children.size());
        }

        Element child = (Element) children.get(0);
        String name = child.getName();
        return name.substring(0, name.length() - 1);
    }

    /**
     * Get the number of items (in the given response model) that
     * were returned
     *
     * @return
     */
    Object getResponseSize() {
        Element element = this.dom.getRootElement();
        return element.getAttributeValue("total_rows");
    }

    /**
     * Get the row packed 2D affine transform to align a SectionImage to it's SectionDataSet
     *
     * @return row packed 2D affine matrix, or null if it does not exist in the file.
     */
    public double[] getRowPackedSection2VolumeTransform() {
        Element alignment2d = getElement("alignment2d");
        if (alignment2d == null) {
            throw new RuntimeException("There is no element 'aligment2d' element in this xml file.");
        }

        double[] vect = new double[6];
        int[] index = new int[]{0, 3, 1, 4, 2, 5};
        int i = 0;
        for (Object obj : alignment2d.getChildren()) {
            Element element = (Element) obj;
            if (element.getName().contains("tsv-")) {
                vect[index[i++]] = Double.parseDouble(element.getValue());
            }
        }

        return vect;
    }

    /**
     * Get the row packed 3D affine transformation to align a SectionDataset to the reference volume.
     *
     * @return row packed 3D affine matrix or null if not existing in the document.
     */
    public double[] getRowPackedVolume2ReferenceTransform() {
        Element alignment3d = getElement("alignment3d");
        if (alignment3d == null) {
            throw new RuntimeException("There is no element 'alignment3d' in this xml file.");
        }

        double[] vect = new double[12];
        int[] index = new int[]{0, 4, 8, 1, 5, 9, 2, 6, 10, 3, 7, 11};
        int i = 0;
        for (Object obj : alignment3d.getChildren()) {
            Element element = (Element) obj;
            if (element.getName().contains("trv-")) {
                vect[index[i++]] = Double.parseDouble(element.getValue());
            }
        }

        return vect;
    }

    /**
     * Quick testing
     */
    public static void main(String[] args) throws IOException, TransformerException, URISyntaxException {
        URL url = new URL("http://api.brain-map.org/api/v2/data/query.xml?criteria=model::Product,rma::criteria,[name$il*Reference*]");
        AllenXml xml = new AllenXml(url);
        xml.printElements(xml.getDom().getRootElement());

        System.out.println("\nGet the value of the first 'name' tag: " + xml.getValue("name").toString());

        System.out.println("Get all the values of the 'name' tags: ");
        for (Object obj : xml.getValues("name")) {
            System.out.println("\t" + obj.toString());
        }

        System.out.println("\nData model: " + xml.getResponseModel());
    }
}
