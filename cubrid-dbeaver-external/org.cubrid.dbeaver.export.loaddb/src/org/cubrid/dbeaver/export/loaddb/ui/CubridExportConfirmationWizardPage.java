package org.cubrid.dbeaver.export.loaddb.ui;

import java.util.List;

import org.cubrid.dbeaver.export.loaddb.model.CubridExportObjectInfo;
import org.cubrid.dbeaver.export.loaddb.model.CubridExportSettings;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.jkiss.dbeaver.ext.cubrid.model.CubridUser;

public class CubridExportConfirmationWizardPage extends WizardPage {

    private static final String NEW_LINE = System.lineSeparator();
    private static final String TAB_SPACE = "        ";
    private CubridExportSettings settings;
    private CubridUser selectedSchema;
    private Text confirmText;

    public CubridExportConfirmationWizardPage(
        CubridLoadDBExportWizard wizard,
        CubridExportSettings settings,
        CubridUser selectedSchema
    ) {
        super("Confirmation Page");
        setTitle("Confirm export options");
        setDescription("Review the export configuration before exporting");
        this.settings = settings;
        this.selectedSchema = selectedSchema;
    }

    @Override
    public void createControl(Composite parent) {
        Composite container = new Composite(parent, SWT.NONE);
        container.setLayout(new GridLayout(1, false));
        setControl(container);
        Composite borderComposite = new Composite(container, SWT.BORDER);
        borderComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        borderComposite.setLayout(new GridLayout(1, false));
        confirmText = new Text(borderComposite, SWT.READ_ONLY | SWT.WRAP | SWT.MULTI);
        confirmText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        setPageComplete(true);
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            buildConfirmText();
        }
    }

    private void buildConfirmText() {
        List<CubridExportObjectInfo> exportObjs = settings.getExportObjects();
        StringBuilder info = new StringBuilder();

        info.append("Export objects: ");
        info.append(selectedSchema != null ? "(For " + selectedSchema.getName() + " Only)" : "(For All Schemas)");
        info.append(NEW_LINE);
        for (CubridExportObjectInfo exportObj : exportObjs) {
            if (exportObj.isExport()) {
                info.append(TAB_SPACE).append(exportObj.getName());
                if (exportObj.getName().equals("Schema")) {
                    if (settings.isSplitSchemaFile()) {
                        info.append(NEW_LINE).append(TAB_SPACE).append(TAB_SPACE);
                        info.append("- Split Schema Files");
                    }
                    if (settings.isExportStartValue()) {
                        info.append(NEW_LINE).append(TAB_SPACE).append(TAB_SPACE);
                        info.append("- Use auto increment value (current)");
                    }
                }
                info.append(NEW_LINE);
            }
        }
        info.append(NEW_LINE);

        info.append("Location:").append(NEW_LINE);
        info.append(TAB_SPACE).append(settings.getOutputFolderPattern());
        info.append(NEW_LINE).append(NEW_LINE);

        info.append("Charset:").append(NEW_LINE);
        info.append(TAB_SPACE).append(settings.getCharset());
        info.append(NEW_LINE).append(NEW_LINE);

        info.append("Tables:").append(NEW_LINE);
        List<String> tables = settings.getTables();
        if (tables != null) {
            for (String table : tables) {
                info.append(TAB_SPACE).append(table).append(NEW_LINE);
            }
        }
        confirmText.setText(info.toString().trim());
    }
}
