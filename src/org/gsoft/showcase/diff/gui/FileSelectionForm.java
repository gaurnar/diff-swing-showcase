package org.gsoft.showcase.diff.gui;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;

public class FileSelectionForm extends JFrame {
    private class BrowseForFileActionListener implements ActionListener {
        private final JTextField relatedTextField;

        private BrowseForFileActionListener(JTextField relatedTextField) {
            this.relatedTextField = relatedTextField;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JFileChooser fileChooser = new JFileChooser();

            int result = fileChooser.showOpenDialog(FileSelectionForm.this);

            if (result == JFileChooser.APPROVE_OPTION) {
                relatedTextField.setText(fileChooser.getSelectedFile().getAbsolutePath());
                checkForInputComplete();
            }
        }
    }

    private JTextField fileATextField;
    private JButton fileABrowseButton;
    private JTextField fileBTextField;
    private JButton fileBBrowseButton;
    private JButton runDiffButton;
    private JPanel rootPanel;

    public FileSelectionForm() {
        setTitle("Choose file to diff");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        setContentPane(rootPanel);
        pack();

        fileABrowseButton.addActionListener(new BrowseForFileActionListener(fileATextField));
        fileBBrowseButton.addActionListener(new BrowseForFileActionListener(fileBTextField));

        runDiffButton.addActionListener(e -> {
            DiffForm diffForm = new DiffForm(fileATextField.getText(), fileBTextField.getText(),
                    Collections.emptyList()); // TODO

            diffForm.setLocationRelativeTo(null);
            diffForm.setVisible(true);

            FileSelectionForm.this.setVisible(false);
        });
    }

    private void checkForInputComplete() {
        runDiffButton.setEnabled(!fileATextField.getText().isEmpty() && !fileBTextField.getText().isEmpty());
    }
}