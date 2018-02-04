import org.gsoft.showcase.diff.gui.FileSelectionForm;

import javax.swing.*;

public class DiffApplication {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            FileSelectionForm fileSelectionForm = new FileSelectionForm();
            fileSelectionForm.setLocationRelativeTo(null);
            fileSelectionForm.setVisible(true);
        });
    }

}
