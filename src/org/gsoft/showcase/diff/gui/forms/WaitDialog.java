package org.gsoft.showcase.diff.gui.forms;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class WaitDialog extends JDialog {
    private JPanel contentPane;
    private JButton buttonCancel;

    public WaitDialog(AtomicBoolean stopFlag) {
        setContentPane(contentPane);
        setModal(true);
        setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        setPreferredSize(new Dimension(200, 100));
        setResizable(false);

        buttonCancel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        buttonCancel.addActionListener(e -> {
            stopFlag.set(true);
            dispose();
        });

        pack();
    }
}
