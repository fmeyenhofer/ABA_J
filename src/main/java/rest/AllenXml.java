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
     * Constructor
     *
     * @param url to query the Allen API
     * @throws TransformerException
     * @throws IOException
     * @throws URISyntaxException
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
     * @throws IOException
     */
    private void load(Scanner scanner) throws IOException {
        String str = "";
        while (scanner.hasNext()) {
            str += scanner.nextLine();
        }
        scanner.close();

        SAXBuilder builder = new SAXBuilder();
        try {
            dom = builder.build(new ByteArrayInputStream(str.getBytes()));
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

    Document getDom() {
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
    Object getValue(String tag_name) {
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
    Object getValue(String tag_name, Element element) {
        if (element.getName().equals(tag_name)) {
            return element.getContent();
        }

        List children = element.getChildren();
        if (children.size() > 0) {
            for (Object obj : children) {
                Object value = getValue(tag_name, (Element) obj);
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
    List getValues(String tag_name) {
        return getValues(tag_name, this.dom.getRootElement());
    }

    /**
     * get the values of all the tags of a given name in the xml element.
     *
     * @param tag_name of the xml tag
     * @param element xml content
     * @return tag content
     */
    List getValues(String tag_name, Element element) {
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
    void getValues(String tag_name, Element element, ArrayList<List> result) {
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
     * Quick testing
     *
     * @param args
     * @throws IOException
     * @throws TransformerException
     * @throws URISyntaxException
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
