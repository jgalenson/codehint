package codehint;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

public class PreferenceInitializer extends AbstractPreferenceInitializer {

	@Override
	public void initializeDefaultPreferences() {
	    IPreferenceStore store = Activator.getDefault().getPreferenceStore();
	    store.setDefault(PreferencePage.DATA_REPORT_PREFNAME, true);
	}

}
