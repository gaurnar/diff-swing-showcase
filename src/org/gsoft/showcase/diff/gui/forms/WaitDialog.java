package org.gsoft.showcase.diff.gui.forms;

import javax.swing.*;

public class WaitDialog extends JDialog {
    public WaitDialog() {
        setTitle("Please wait");
        setModal(true);
        setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        JOptionPane optionPane = new JOptionPane("Calculating diff, please wait...",
                JOptionPane.INFORMATION_MESSAGE, JOptionPane.DEFAULT_OPTION,
                null, new Object[]{}, null);
        setContentPane(optionPane);

        pack();
    }
}
