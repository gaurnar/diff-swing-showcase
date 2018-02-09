package org.gsoft.showcase.diff.gui;

import org.gsoft.showcase.diff.logic.DiffGeneratorUtils;
import org.gsoft.showcase.diff.logic.DiffGeneratorUtils.TextsLinesEncoding;
import org.gsoft.showcase.diff.logic.MyersDiffGenerator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FileSelectionForm extends JFrame {
    private class BrowseForFileActionListener implements ActionListener {
        private final JTextField relatedTextField;

        private BrowseForFileActionListener(JTextField relatedTextField) {
            this.relatedTextField = relatedTextField;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JFileChooser fileChooser;
            if (selectedDirectoryPath != null) {
                fileChooser = new JFileChooser(selectedDirectoryPath);
            } else {
                fileChooser = new JFileChooser();
            }

            int result = fileChooser.showOpenDialog(FileSelectionForm.this);

            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                relatedTextField.setText(selectedFile.getAbsolutePath());
                selectedDirectoryPath = selectedFile.getParent();
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

    private String selectedDirectoryPath;

    public FileSelectionForm() {
        setTitle("Choose file to diff");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        // TODO can it be done in designer?
        fileABrowseButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        fileBBrowseButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        runDiffButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        setContentPane(rootPanel);
        pack();

        fileABrowseButton.addActionListener(new BrowseForFileActionListener(fileATextField));
        fileBBrowseButton.addActionListener(new BrowseForFileActionListener(fileBTextField));

        runDiffButton.addActionListener((e) -> runDiff());
    }

    private void runDiff() {
        FileSelectionForm.this.setVisible(false);

        WaitDialog waitDialog = new WaitDialog();
        waitDialog.setLocationRelativeTo(null);

        new Thread(() -> {
            try {
                TextsLinesEncoding textsLinesEncoding = DiffGeneratorUtils.encodeTexts(
                        readFileIntoStringsSplit(fileATextField.getText()),
                        readFileIntoStringsSplit(fileBTextField.getText()));

                DiffForm diffForm = new DiffForm(fileATextField.getText(), fileBTextField.getText(),
                        new MyersDiffGenerator().generate(textsLinesEncoding.getTextA(), textsLinesEncoding.getTextB()),
                        textsLinesEncoding);

                diffForm.setLocationRelativeTo(null);
                diffForm.setVisible(true);
            } catch (Throwable t) {
                JOptionPane.showMessageDialog(waitDialog,
                        String.format("Failed to compute diff!\n%s: %s", t.getClass().getSimpleName(), t.getMessage()),
                        "Error", JOptionPane.ERROR_MESSAGE);
                t.printStackTrace();
                System.exit(1);
            }

            waitDialog.dispose();
        }).start();

        waitDialog.setVisible(true);
    }

    private void checkForInputComplete() {
        runDiffButton.setEnabled(!fileATextField.getText().isEmpty() && !fileBTextField.getText().isEmpty());
    }

    private static String[] readFileIntoStringsSplit(String path) {
        // TODO support other encodings
        String fileContents = readFile(path, StandardCharsets.UTF_8);

        // TODO handle mixed line endings
        String lineEndings = fileContents.contains("\r\n") ? "\r\n" : "\n";

        return fileContents.split(lineEndings);
    }

    /**
     * Taken from: https://stackoverflow.com/a/326440
     * TODO use commons
     */
    private static String readFile(String path, Charset encoding)
    {
        byte[] encoded;
        try {
            encoded = Files.readAllBytes(Paths.get(path));
        } catch (IOException e) {
            throw new RuntimeException("error reading file!");
        }
        return new String(encoded, encoding);
    }
}