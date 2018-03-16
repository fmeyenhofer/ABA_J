package rest;

import javax.xml.transform.TransformerException;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;

/**
 * Class to handle the image files from the Allen API
 *
 * TODO: implement progress tracker and notifier interface
 *
 * @author Felix Meyenhofer
 */
class AllenImage extends AllenFile {

    /**
     * {@inheritDoc}
     */
    AllenImage(URL url, File file) throws IOException, TransformerException, URISyntaxException {
        super(url, file);
    }

    /**
     * {@inheritDoc}
     */
    AllenImage(File file) throws IOException, URISyntaxException {
        super(file);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void load(URL url) throws IOException {
        // TODO: There was the problem with the "java.net.UnknownHostException: iwarehouse". I added the proper connection object, but with the university networks the problem seemed to occur randomely. So far the research indicated that it might actually be de DN server the causes the problems.
        HttpURLConnection host = (HttpURLConnection) url.openConnection();
        host.connect();

//        System.out.println("content length: " + host.getContentLength());

        InputStream is = host.getInputStream();

        OutputStream os = new FileOutputStream(getFile());
//        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] b = new byte[4096];
        int len;
        while ((len = is.read(b)) != -1) {
            os.write(b, 0, len);
        }
        is.close();
        os.close();

        host.disconnect();

//        byte[] bytes = os.toByteArray();
//        FileOutputStream fos = new FileOutputStream(getFile().getAbsoluteFile());
//        fos.write(bytes);
//        fos.close();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void load(File file) throws IOException, URISyntaxException {
        // not used
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void save() {
        // not used
    }
}
