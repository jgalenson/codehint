package codehint;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

public class PreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

	public static final String DATA_REPORT_PREFNAME = "codehint.reportData";

	@Override
	protected void createFieldEditors() {
	    addField(new BooleanFieldEditor(DATA_REPORT_PREFNAME,  "&Report anonymous usage information", getFieldEditorParent()));
	}

	@Override
	public void init(IWorkbench workbench) {
	    setPreferenceStore(Activator.getDefault().getPreferenceStore());
	    setDescription("CodeHint preferences");
	}

}
