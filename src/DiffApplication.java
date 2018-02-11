import org.gsoft.showcase.diff.gui.forms.FileSelectionForm;

import javax.swing.*;

public class DiffApplication {

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                    "Failed to set \"look and feel\"!\nError message:\n" + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            System.exit(1);
        }

        SwingUtilities.invokeLater(() -> {
            FileSelectionForm fileSelectionForm = new FileSelectionForm();
            fileSelectionForm.setLocationRelativeTo(null);
            fileSelectionForm.setVisible(true);
        });
    }

}
