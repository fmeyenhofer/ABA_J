package rest;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.TreeMap;

/**
 * Class for convenient handling of the json output from the Allen API.
 * it makes the following assumptions about the structure:
 * - the root element contains general attributes about the query (like if it was successful or not)
 * - the message attribute of the root element contains all the returned elements
 *
 * TODO: add a field that indicates model in the url and the one of the response (might have to add an xml query to get it)
 *
 * @author Felix Meyenhofer
 */
class AllenJson extends AllenFile {


    /** The returned JSON object */
    private JSONObject obj;

    /** the returned elements (contained in the "msg" attribute of the root... for easy access) */
    private JSONArray items;

    /**
     * Constructor
     *
     * @param url to query the Allen API
     * @throws IOException in case of a bad URL, or problems with the stream
     */
    AllenJson(URL url) throws IOException, TransformerException, URISyntaxException {
        super(url, null);
    }

    /**
     * {@inheritDoc}
     */
    AllenJson(URL url, File file) throws IOException, TransformerException, URISyntaxException {
        super(url, file);
    }

    /**
     * {@inheritDoc}
     */
    AllenJson(File file) throws IOException, URISyntaxException {
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
    void load(URL url) throws IOException, TransformerException, URISyntaxException {
        load(new Scanner(url.openStream()));
    }

    /**
     * Load the content
     *
     * @param scanner
     */
    private void load(Scanner scanner) {
        String str = "";
        while (scanner.hasNext()) {
            str += scanner.nextLine();
        }
        scanner.close();

        obj = new JSONObject(str);

        if (!obj.get("success").equals(true)) {
            throw new RuntimeException("The URL '" + getUrl() + "' did not yield a valid response.");
        }

        items = (JSONArray) obj.get("msg");

        if (items.length() < 1) {
            throw new RuntimeException("The URL '" + getUrl() + "' did not return any items.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void save() throws IOException {
        FileWriter writer = new FileWriter(getFile().getAbsolutePath());
        writer.write(this.obj.toString(3));
        writer.flush();
        writer.close();
    }

    /**
     * Get the number of items returned by the query
     *
     * @return
     */
    Object getResponseSize() {
        return this.obj.get("total_rows");
    }

    /**
     * Get the value of the result. This can be used if exactly one element is returned
     * (if there are more or less elements it will fail)
     *
     * @param key of the value that has to be returned (attribute name)
     * @return value of the atrribute
     */
    Object getValue(String key) {
        if (items.length() != 1) {
            throw new RuntimeException("There are more than one items.");
        }

        for (Object obj : items) {
            JSONObject item = (JSONObject) obj;

            if (item.has(key)) {
                return item.get(key);
            }
        }

        return null;
    }

    /**
     * Get all the values of a given attribute (key), of all the returned elements of the
     * first encapsulation (indentation) level.
     *
     * @param key of the value (attribute name)
     * @return {@link ArrayList<Object>} the list of values
     */
    ArrayList<Object> getValues(String key) {
        ArrayList<Object> values = new ArrayList<>();

        for (Object obj : items) {
            JSONObject item = (JSONObject) obj;
            if (item.has(key)) {
                values.add(item.get(key));
            }
        }

        return values;
    }

    /**
     * Get all the values of a given attribute of the sorted set of objects
     *
     * @param key name of the desired attribute
     * @param sortBy name of the attribute the object will be sorted
     * @return list of attribute values
     */
    ArrayList<Object> getValues(String key, String sortBy) {
        TreeMap<String, Object> map = new TreeMap<>();

        for (Object obj : items) {
            JSONObject item = (JSONObject) obj;
            if (item.has(key) && item.has(sortBy)) {
                map.put(item.get(sortBy).toString(), item.get(key));
            }
        }

        return new ArrayList<>(map.values());
    }

    /**
     * Quick testing
     *
     * @param args nothing
     * @throws IOException that happened
     */
    public static void main(String[] args) throws IOException, TransformerException, URISyntaxException {
        AllenJson json = new AllenJson(new URL("http://api.brain-map.org/api/v2/data/query.json?criteria=model::AtlasImage,rma::criteria,data_set[id$eq100048576]"));
        JSONObject msg = new JSONObject(json.obj.get("msg"));

        for (String key : msg.keySet()) {
            System.out.println(key + ": " + msg.get(key));
        }

        System.out.println("");
    }
}
