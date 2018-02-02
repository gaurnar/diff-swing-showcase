import org.gsoft.showcase.diff.gui.DiffForm;
import org.gsoft.showcase.diff.logic.*;

import javax.swing.*;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class DiffApplication {

    public static void main(String[] args) {
//        List<MyersDiffGenerator.EditPathEdge> edges = new MyersDiffGenerator().doMyers("abcabba", "cbabac");

        SwingUtilities.invokeLater(() -> {
            // TODO select files
//            FileSelectionForm fileSelectionForm = new FileSelectionForm();
//            fileSelectionForm.setLocationRelativeTo(null);
//            fileSelectionForm.setVisible(true);

            DiffForm diffForm = new DiffForm("C:\\file1.txt", "C:\\file2.txt", generateTestDiff());
            diffForm.setLocationRelativeTo(null);
            diffForm.setVisible(true);
        });
    }

    private static List<DiffListItem> generateTestDiff() {
        EqualStringsItem bigEqualPrefix = new EqualStringsItem(
                Stream.generate(() -> "aaaaa aa aaaaa a aaaaaaaaaaaaaa\n").limit(100).collect(Collectors.joining()));

        DeletedStringsItem deleted = new DeletedStringsItem(
                Stream.generate(() -> "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\n").limit(10).collect(Collectors.joining()));

        EqualStringsItem inBetween = new EqualStringsItem(
                Stream.generate(() -> "cccccc ccccccccc cccccccccc\n").limit(10).collect(Collectors.joining()));

        InsertedStringsItem inserted = new InsertedStringsItem(
                Stream.generate(() -> "dddddddddddddddddddddddddddd\n").limit(10).collect(Collectors.joining()));

        EqualStringsItem bigEqualSuffix = new EqualStringsItem(
                Stream.generate(() -> "eeeeee eeee eeeeeeeee eeee eeeee\n").limit(100).collect(Collectors.joining()));

        return Arrays.asList(bigEqualPrefix, deleted, inBetween, inserted, bigEqualSuffix);
    }
}
