package gui;

import ij.ImagePlus;

import net.imagej.ImageJ;
import net.imagej.display.ImageDisplay;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * Dialog to make a ordered selection among listed items.
 *
 * @author Felix Meyenhofer
 */
public class OrderedListSelectionDialog extends JDialog implements ActionListener, ListSelectionListener, KeyListener {

    /** Attribute Names in the order of selection */
    private TreeMap<Integer, String> selection;

    /** Table with the selection order and the attribute name */
    private JTable table;

    /** Flag to check if the dialog was cancelled */
    private boolean cancelled = false;

    /** Window title */
    private static final String DIALOG_TITLE = "Image selector";

    /** Column names of the selection table */
    private static final String[] COLUMN_NAMES = {"order", "image"};


    private OrderedListSelectionDialog(List<String> data) {
        // Ok and cancel buttons
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.LINE_AXIS));
        buttonPanel.add(Box.createHorizontalGlue());
        JButton buttonCancel = new JButton("Cancel");
        buttonCancel.addActionListener(this);
        buttonPanel.add(buttonCancel);
        buttonPanel.add(Box.createRigidArea(new Dimension(10, 0)));
        /** Dialog confirmation button */
        JButton buttonOK = new JButton("OK");
        buttonOK.addActionListener(this);
        buttonPanel.add(buttonOK);

        // Create the table
        DefaultTableModel model = new DefaultTableModel(new String[data.size()][2], COLUMN_NAMES);

        int index = 0;
        for (String item : data) {
            model.setValueAt("-", index,0);
            model.setValueAt(item, index++, 1);
        }

        table = new JTable(model) {
            @Override
            public boolean isCellEditable ( int row, int column )
            {
                return false;
            }
        };

        ListSelectionModel listSelectionModel = table.getSelectionModel();
        listSelectionModel.addListSelectionListener(this);
        table.setSelectionModel(listSelectionModel);
        table.getColumnModel().getColumn(0).setMaxWidth(40);
        table.getColumnModel().getColumn(0).setPreferredWidth(40);
        table.getColumnModel().getColumn(0).setMinWidth(40);
        table.addKeyListener(this);

        JScrollPane tablePanel = new JScrollPane();
        tablePanel.setToolTipText("Use Shift and Ctrl/Cmd to select multiple items.");
        tablePanel.setViewportView(table);
        tablePanel.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        // Assemble the different sections in one pane.
        this.setLayout(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridy = 0;
        constraints.gridx = 0;
        constraints.weighty = 0.8;
        constraints.weightx = 1;
        constraints.insets = new Insets(7, 7, 7, 7);
        constraints.fill = GridBagConstraints.BOTH;
        this.add(tablePanel, constraints);
        constraints.gridy = 1;
        constraints.weighty = 0.01;
        constraints.gridy = 2;
        this.add(buttonPanel, constraints);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cancelled = true;
                super.windowClosing(e);
            }
        });

        this.setTitle(DIALOG_TITLE);
        this.getRootPane().setDefaultButton(buttonOK);
    }

    public static OrderedListSelectionDialog createAndShow(List<String> list) {
        OrderedListSelectionDialog dialog = new OrderedListSelectionDialog(list);

        dialog.setModal(true);
        dialog.setLocationByPlatform(true);
        dialog.setSize(new Dimension(300,270));
        dialog.selectAll();
//        dialog.getRootPane().setDefaultButton(dialog.buttonOK);
//        dialog.buttonOK.requestFocus();
        dialog.setVisible(true);
        dialog.pack();

        return dialog;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public ArrayList<String> getSelection() {
        ArrayList<String> data = new ArrayList(selection.size());

        for (Integer index: selection.keySet()) {
            data.add(index-1, selection.get(index));
        }
        return data;
    }

    public void selectAll() {
        for (int i = 0; i < table.getRowCount(); i++) {
            table.setValueAt(Integer.toString(i), i, 0);
        }

        ListSelectionModel model = table.getSelectionModel();
        model.setSelectionInterval(0, table.getRowCount() - 1);
        
        repaint();
    }

    private void updateSelection() {
        selection = new TreeMap<>();
        for (int i = 0; i < table.getRowCount(); i++) {
            if (!table.getValueAt(i, 0).equals("-")) {
                selection.put(Integer.parseInt((String) table.getValueAt(i, 0)),
                        (String) table.getValueAt(i, 1));
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void actionPerformed(ActionEvent e) {
        if (!e.getActionCommand().equals("OK")) {
            cancelled = true;
        }

        this.dispose();
    }

    /** {@inheritDoc} */
    @Override
    public void valueChanged(ListSelectionEvent event) {
        ListSelectionModel lsm = (ListSelectionModel) event.getSource();

        // Only use one of the actions
        if (lsm.getValueIsAdjusting()){
            return;
        }

        // fetch previous index map (order, position) and the indices of the selected rows
        HashMap<String, Integer> indexMap = new HashMap<>();
        ArrayList<Integer> selectedIndices = new ArrayList<>();

        for (int i=0; i <table.getRowCount(); i++) {

            if (lsm.isSelectedIndex(i)) {
                if (!table.getValueAt(i,0).equals("-")) {
                    indexMap.put((String) table.getValueAt(i, 0), i);
                }
                selectedIndices.add(i);
            } else if (!table.getValueAt(i,0).equals("-")) {
                table.setValueAt("-", i, 0);
            }
        }

        // Sort according the order
        List<String> orders = new ArrayList<>(indexMap.keySet());
        Collections.sort(orders);

        // Re-index if one selection dropped out. Also invert keys and values for the next step
        HashMap<Integer, String> validatedIndexMap = new HashMap<>();
        int newOrder = 1;
        for (String order : orders) {
            validatedIndexMap.put(indexMap.get(order), "" + newOrder);
            newOrder++;
        }

        // Update the table
        int order = indexMap.size();
        for (Integer pos : selectedIndices) {

            if (validatedIndexMap.containsKey(pos)) {
                table.setValueAt(validatedIndexMap.get(pos), pos, 0);
            } else {
                ++order;
                table.setValueAt("" + order, pos, 0);
            }
        }

        updateSelection();

        repaint();
    }

    @Override
    public void keyTyped(KeyEvent e) {
        if (e.getExtendedKeyCode() == KeyEvent.VK_ENTER) {
            updateSelection();
            this.dispose();
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        // nothing
    }

    @Override
    public void keyReleased(KeyEvent e) {
        // nothing
    }


    /**
     * Quick testing
     *
     * @param args whatever
     */
    public static void main(String[] args) {
        ImageJ ij = new ImageJ();
        ij.ui().showUI();
        ImagePlus imp1 = new ImagePlus("http://imagej.nih.gov/ij/images/blobs.gif");
        ij.display().createDisplay(imp1);
        ImagePlus imp2 = new ImagePlus("http://imagej.nih.gov/ij/images/baboon.jpg");
        ij.display().createDisplay(imp2);
        ImagePlus imp3 = new ImagePlus("http://imagej.nih.gov/ij/images/cat.jpg");
        ij.display().createDisplay(imp3);

        List<String> titles = new ArrayList();
        for (ImageDisplay display : ij.imageDisplay().getImageDisplays()) {
            titles.add(display.getActiveView().getData().getName());
        }

        OrderedListSelectionDialog dialog =  OrderedListSelectionDialog.createAndShow(titles);

        for (String item : dialog.getSelection()) {
            System.out.println(item);
        }

        System.exit(0);
    }
}

