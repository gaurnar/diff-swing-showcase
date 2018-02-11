package org.gsoft.showcase.diff.gui;

import org.gsoft.showcase.diff.generators.DiffGeneratorUtils;
import org.gsoft.showcase.diff.generators.DiffGeneratorUtils.LinesEncoding;
import org.gsoft.showcase.diff.generators.DiffItem;
import org.gsoft.showcase.diff.generators.DiffItemType;
import org.gsoft.showcase.diff.generators.impl.MyersDiffGenerator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

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
                String selectedFilePath = selectedFile.getAbsolutePath();

                try {
                    if (isBinaryFile(selectedFilePath)) {
                        int response = JOptionPane.showConfirmDialog(FileSelectionForm.this,
                                "Selected file looks like a binary file. Are you sure you want to continue?", "Binary file",
                                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                        if (response != JOptionPane.YES_OPTION) {
                            return;
                        }
                    }
                } catch (Throwable t) {
                    JOptionPane.showMessageDialog(FileSelectionForm.this,
                            String.format("%s: %s", t.getClass().getSimpleName(), t.getMessage()),
                            "Error", JOptionPane.ERROR_MESSAGE);
                    t.printStackTrace();
                    return;
                }

                relatedTextField.setText(selectedFilePath);
                selectedDirectoryPath = selectedFile.getParent();
                validateSelectedFiles();
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

        setResizable(false);

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
                LinesEncoding linesEncoding = DiffGeneratorUtils.encodeLines(
                        readFileIntoStringsSplit(fileATextField.getText()),
                        readFileIntoStringsSplit(fileBTextField.getText()));

                List<DiffItem> byLineDiffItems = new MyersDiffGenerator().generate(
                        linesEncoding.getLinesA(), linesEncoding.getLinesB());

                if ((byLineDiffItems.size() == 1) && (byLineDiffItems.get(0).getType() == DiffItemType.EQUAL)) {
                    JOptionPane.showMessageDialog(waitDialog, "Files are equal!", "Diff", JOptionPane.INFORMATION_MESSAGE);
                }

                DiffForm diffForm = new DiffForm(fileATextField.getText(), fileBTextField.getText(),
                        byLineDiffItems, linesEncoding);

                waitDialog.dispose();

                diffForm.setLocationRelativeTo(null);
                diffForm.setVisible(true);
            } catch (Throwable t) {
                JOptionPane.showMessageDialog(waitDialog,
                        String.format("Failed to compute diff!\n%s: %s", t.getClass().getSimpleName(), t.getMessage()),
                        "Error", JOptionPane.ERROR_MESSAGE);
                t.printStackTrace();
                System.exit(1);
            }
        }).start();

        waitDialog.setVisible(true);
    }

    private void validateSelectedFiles() {
        if (fileATextField.getText().equals(fileBTextField.getText())) {
            JOptionPane.showMessageDialog(this, "File A and file B are the same files!",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        runDiffButton.setEnabled(!fileATextField.getText().isEmpty() && !fileBTextField.getText().isEmpty());
    }

    private boolean isBinaryFile(String path) {
        //
        // just checking first kilobyte of the file for NULs - they should not be in a text file
        // (ignoring something like UTF-16 - we don't support it yet)
        //
        byte[] buffer = new byte[1024];
        try (FileInputStream fileInputStream = new FileInputStream(path)) {
            int bytesRead = fileInputStream.read(buffer);
            for (int i = 0; i < bytesRead; i++) {
                if (buffer[i] == 0) {
                    return true;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return false;
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