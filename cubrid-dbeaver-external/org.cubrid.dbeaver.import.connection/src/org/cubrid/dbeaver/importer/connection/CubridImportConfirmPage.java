package org.cubrid.dbeaver.importer.connection;

import java.util.List;

import org.cubrid.dbeaver.importer.connection.CubridImportPrefsParser.CMDatabase;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.jkiss.dbeaver.model.DBIcon;
import org.jkiss.dbeaver.ui.DBeaverIcons;

public class CubridImportConfirmPage extends WizardPage implements IWizardPage {

    private final CubridImportConnectionWizard wizard;
    private CheckboxTableViewer viewer;
    private Button selectAllCheckbox;

    public CubridImportConfirmPage(CubridImportConnectionWizard wizard) {
        super("Confirm");
        this.wizard = wizard;
        setTitle("Confirm import");
        setDescription("Select the databases you want to import.");
    }

    @Override
    public void createControl(Composite parent) {
        Composite root = new Composite(parent, SWT.NONE);
        root.setLayout(new GridLayout(1, false));

        viewer = CheckboxTableViewer.newCheckList(root, SWT.BORDER | SWT.FULL_SELECTION);
        Table table = viewer.getTable();
        table.setHeaderVisible(true);
        table.setLinesVisible(false);
        table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        viewer.setContentProvider(ArrayContentProvider.getInstance());
        viewer.setLabelProvider(new DbLabelProvider());

        createColumn(table, "Database", 220);
        createColumn(table, "Host", 160);
        createColumn(table, "Port", 80);
        createColumn(table, "User", 120);

        Composite footer = new Composite(root, SWT.NONE);
        footer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        footer.setLayout(new GridLayout(1, false));

        selectAllCheckbox = new Button(footer, SWT.CHECK);
        selectAllCheckbox.setText("Select all");
        selectAllCheckbox.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
        selectAllCheckbox.setEnabled(false);

        selectAllCheckbox.addListener(SWT.Selection, e -> {
            boolean checked = selectAllCheckbox.getSelection();
            viewer.setAllChecked(checked);
            updatePageComplete();
        });

        viewer.addCheckStateListener(new ICheckStateListener() {
            @Override
            public void checkStateChanged(CheckStateChangedEvent event) {
                syncSelectAllState();
                updatePageComplete();
            }
        });

        setControl(root);
        setPageComplete(false);
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);

        if (!visible || viewer == null) {
            return;
        }

        if (wizard.getParseError() != null) {
            setErrorMessage("Parse error: " + wizard.getParseError());
            viewer.setInput(List.of());
            viewer.setAllChecked(false);
            selectAllCheckbox.setEnabled(false);
            selectAllCheckbox.setSelection(false);
            setPageComplete(false);
            return;
        } else {
            setErrorMessage(null);
        }

        List<CMDatabase> dbs = wizard.getDatabases();
        viewer.setInput(dbs);

        boolean hasRows = !dbs.isEmpty();
        viewer.setAllChecked(false);
        selectAllCheckbox.setEnabled(hasRows);
        selectAllCheckbox.setSelection(false);

        updatePageComplete();
    }

    public CMDatabase[] getCheckedDatabases() {
        Object[] checked = viewer.getCheckedElements();
        CMDatabase[] result = new CMDatabase[checked.length];
        for (int i = 0; i < checked.length; i++) {
            result[i] = (CMDatabase) checked[i];
        }
        return result;
    }

    private void updatePageComplete() {
        setPageComplete(viewer.getCheckedElements().length > 0);
    }

    private void syncSelectAllState() {
        if (selectAllCheckbox == null || selectAllCheckbox.isDisposed()) {
            return;
        }
        int total = viewer.getTable().getItemCount();
        if (total == 0) {
            selectAllCheckbox.setSelection(false);
            return;
        }
        selectAllCheckbox.setSelection(viewer.getCheckedElements().length == total);
    }

    private static void createColumn(Table table, String title, int width) {
        TableColumn col = new TableColumn(table, SWT.LEFT);
        col.setText(title);
        col.setWidth(width);
    }

    private static class DbLabelProvider extends LabelProvider implements ITableLabelProvider {
        @Override
        public String getColumnText(Object element, int columnIndex) {
            CMDatabase db = (CMDatabase) element;
            return switch (columnIndex) {
                case 0 -> db.dbName;
                case 1 -> db.host;
                case 2 -> db.port;
                case 3 -> db.dbUser;
                default -> "";
            };
        }

        @Override
        public Image getColumnImage(Object element, int columnIndex) {
            if (columnIndex == 0) {
                return DBeaverIcons.getImage(DBIcon.TREE_DATABASE);
            }
            return null;
        }
    }
}
