package gui;

import rest.AllenCache;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileFilter;


/**
 * Dialog to select a SectionDataset from the {@link AllenCache}.
 *
 * @author Felix Meyenhofer
 */
public class SectionDatasetSelector extends JPanel implements ActionListener {

    private final String PRODUCT_SELECTOR_NAME = "Select a product";
    private final String DATASET_SELECTOR_NAME = "Select the dataset";
    private final String SAMPLING_SELECTOR_NAME = "Select down-sampling";
    private final String QUALITY_SELECTOR_NAME = "Select the image quality";
    private final String OK_BUTTON_NAME = "OK";
    private final String CANCEL_BUTTON_NAME = "Cancel";

    private File[] products;
    private File[] datasets;
    private File[] samplings;
    private File[] qualities;

    private File selection;

    private JComboBox<String> productSelector;
    private JComboBox<String> datasetSelector;
    private JComboBox<String> samplingSelector;
    private JComboBox<String> qualitySelector;

    private SectionDatasetSelector() {
        super();

        AllenCache cache = new AllenCache();
        File root = cache.getDirectory(AllenCache.DataType.img);
        products = getSubdirectories(root);
        String[] productChoices = new String[products.length + 1];
        int i = 0;
        productChoices[i++] = "";
        for (String item : getNames(products)) {
            productChoices[i++] = item;
        }

        qualitySelector = new JComboBox<>();
        qualitySelector.setName(QUALITY_SELECTOR_NAME);
        qualitySelector.setEnabled(false);

        samplingSelector = new JComboBox<>();
        samplingSelector.setName(SAMPLING_SELECTOR_NAME);
        samplingSelector.setEnabled(false);
        samplingSelector.addActionListener(this);

        datasetSelector = new JComboBox<>();
        datasetSelector.setName(DATASET_SELECTOR_NAME);
        datasetSelector.setEnabled(false);
        datasetSelector.addActionListener(this);

        productSelector = new JComboBox<>(productChoices);
        productSelector.setName(PRODUCT_SELECTOR_NAME);
        productSelector.addActionListener(this);

        JButton button1 = new JButton(OK_BUTTON_NAME);
        button1.setName(OK_BUTTON_NAME);
        button1.addActionListener(this);
        JButton button2 = new JButton(CANCEL_BUTTON_NAME);
        button2.setName(CANCEL_BUTTON_NAME);
        button2.addActionListener(this);
        JPanel panel = new JPanel();
        panel.setBorder(new EmptyBorder(10, 0, 0, 0));
        panel.add(button1);
        panel.add(button2);

        this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        this.setBorder(new EmptyBorder(10, 10, 10, 10));
        this.add(productSelector);
        this.add(datasetSelector);
        this.add(samplingSelector);
        this.add(qualitySelector);
        this.add(panel);
    }

    public static SectionDatasetSelector createAndShow() {
        SectionDatasetSelector dialog = new SectionDatasetSelector();

        JDialog frame = new JDialog();
        frame.setTitle("Select a SectionImage directory");
        frame.add(dialog);
        frame.setModal(true);
        frame.setSize(new Dimension(300,250));
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
        frame.pack();

        dialog.update();

        return dialog;
    }

    public File getSelection() {
        return selection;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        update(((Component) e.getSource()).getName());
    }

    private void update() {
        update(this.PRODUCT_SELECTOR_NAME);
    }

    private void update(String control) {

        switch (control) {

            case PRODUCT_SELECTOR_NAME:
                disable(3);
                int productIndex = productSelector.getSelectedIndex();
                if (productIndex > 0) {
                    File product = products[productIndex - 1];
                    datasetSelector.removeAllItems();
                    datasets = getSubdirectories(product);
                    for (String item : getNames(datasets)) {
                        datasetSelector.addItem(item);
                    }
                    datasetSelector.setEnabled(true);
                }
                break;

            case DATASET_SELECTOR_NAME:
                disable(2);
                int datasetIndex = datasetSelector.getSelectedIndex();
                if (datasetIndex > -1) {
                    qualitySelector.setEnabled(false);
                    qualitySelector.removeAllItems();
                    File dataset = datasets[datasetIndex];
                    samplings = getSubdirectories(dataset);
                    for (String item : getNames(samplings)) {
                        samplingSelector.addItem(item);
                    }
                    samplingSelector.setEnabled(true);
                }
                break;

            case SAMPLING_SELECTOR_NAME:
                disable(1);
                int samplingIndex = samplingSelector.getSelectedIndex();
                if (samplingIndex > -1) {
                    File sampling = samplings[samplingIndex];
                    qualities = getSubdirectories(sampling);
                    for (String item : getNames(qualities)) {
                        qualitySelector.addItem(item);
                    }
                    qualitySelector.setEnabled(true);
                }
                break;

            case QUALITY_SELECTOR_NAME:
                break;

            case OK_BUTTON_NAME:
                int qualityIndex = qualitySelector.getSelectedIndex();
                if (qualityIndex < 0) {
                    JDialog parent = (JDialog) SwingUtilities.getWindowAncestor(this);
                    JOptionPane.showMessageDialog(parent, "You have to select all the way through to a quality.");
                    break;
                }

                selection = qualities[qualityIndex];

            case CANCEL_BUTTON_NAME:
                JDialog dialog = (JDialog) SwingUtilities.getWindowAncestor(this);
                dialog.dispose();
                break;
        }
    }

    private void disable(int level) {
        if (level >= 3) {
            datasetSelector.setEnabled(false);
            datasetSelector.removeAllItems();
            datasetSelector.setSelectedIndex(-1);
        }
        if (level >= 2) {
            samplingSelector.setEnabled(false);
            samplingSelector.removeAllItems();
            samplingSelector.setSelectedIndex(-1);
        }
        if (level >= 1) {
            qualitySelector.setEnabled(false);
            qualitySelector.removeAllItems();
            qualitySelector.setSelectedIndex(-1);
        }
    }

    private File[] getSubdirectories(File file) {
        return file.listFiles(new FileFilter() {
            @Override
            public boolean accept(File item) {
                return item.isDirectory();
            }
        });
    }

    private String[] getNames(File[] files) {
        String[] names = new String[files.length];
        int i = 0;
        for (File file : files) {
            names[i++] = file.getName();
        }

        return names;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                SectionDatasetSelector dialog = createAndShow();
                System.out.println(dialog.getSelection());
                System.exit(0);
            }
        });
    }
}
