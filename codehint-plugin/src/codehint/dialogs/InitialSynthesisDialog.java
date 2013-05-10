package codehint.dialogs;

import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import codehint.Synthesizer.SynthesisWorker;

public class InitialSynthesisDialog extends SynthesisDialog {

	public InitialSynthesisDialog(Shell parentShell, String varTypeName, IJavaType varType, IJavaStackFrame stack, PropertyDialog propertyDialog, SynthesisWorker synthesisWorker) {
		super(parentShell, varTypeName, varType, stack, propertyDialog, synthesisWorker);
	}

	@Override
	protected void addSearchButtons(Composite topButtonComposite) {
		addSearchCancelButton(topButtonComposite, true);
	}

}
