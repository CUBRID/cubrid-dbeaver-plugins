package org.cubrid.dbeaver.importer.connection;

import java.io.File;
import java.nio.file.Path;

import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

public class CubridImportUploadPage extends WizardPage {

    private final CubridImportConnectionWizard wizard;
    private Text fileText;

    public CubridImportUploadPage(CubridImportConnectionWizard wizard) {
        super("Upload the file");
        setTitle("Select CUBRID Manager/Admin .prefs file to import connections");
        setDescription("CA/CM prefs file : com.cubrid.cubridmanager.ui.prefs or com.cubrid.cubridquery.ui.prefs in $workspace/.metadata/.plugins/org.eclipse.core.runtime");
        this.wizard = wizard;
    }

    @Override
    public void createControl(Composite parent) {
        Composite container = new Composite(parent, SWT.NONE);
        container.setLayout(new GridLayout(3, false));

        Label label = new Label(container, SWT.NONE);
        label.setText("CUBRID prefs file:");

        fileText = new Text(container, SWT.BORDER);
        fileText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        fileText.setEditable(false);

        Button browseButton = new Button(container, SWT.PUSH);
        browseButton.setText("Browse...");

        browseButton.addListener(SWT.Selection, e -> {
            handleFileBrowse();
        });

        setControl(container);
        setPageComplete(false);
    }

    private void handleFileBrowse() {
        FileDialog dialog = new FileDialog(getShell(), SWT.OPEN);
        dialog.setText("Select CUBRID Manager prefs file");
        dialog.setFilterExtensions(new String[] { "*.prefs", "*.*" });

        String selected = dialog.open();
        if (selected != null) {
            fileText.setText(selected);
            if (validateFile(selected)) {
                wizard.setSelectedFile(Path.of(selected)); // parses immediately
                if (wizard.getParseError() != null) {
                    setErrorMessage("File loaded but failed to parse: " + wizard.getParseError());
                    setPageComplete(false);
                } else if (wizard.getDatabases().isEmpty()) {
                    setErrorMessage("No databases found in the selected file.");
                    setPageComplete(false);
                } else {
                    setErrorMessage(null);
                    setPageComplete(true);
                }
            }
        }
    }

    private boolean validateFile(String path) {
        File file = new File(path);
        if (!file.exists()) {
            setErrorMessage("File does not exist.");
            setPageComplete(false);
            return false;
        }
        if (!file.getName().toLowerCase().endsWith(".prefs")) {
            setErrorMessage("Please select a valid .prefs file.");
            setPageComplete(false);
            return false;
        }
        setErrorMessage(null);
        return true;
    }
}
