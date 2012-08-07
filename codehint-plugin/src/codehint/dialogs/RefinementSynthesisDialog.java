package codehint.dialogs;

import java.util.ArrayList;

import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import codehint.Synthesizer.RefinementWorker;
import codehint.expreval.EvaluatedExpression;
import codehint.exprgen.TypeCache;

public class RefinementSynthesisDialog extends SynthesisDialog {
	
	private final RefinementWorker worker;
	private final TypeCache typeCache;

	public RefinementSynthesisDialog(Shell parentShell, String varTypeName, IJavaType varType, IJavaStackFrame stack, PropertyDialog propertyDialog, RefinementWorker worker, TypeCache typeCache) {
		super(parentShell, varTypeName, varType, stack, propertyDialog);
		this.worker = worker;
		this.typeCache = typeCache;
	}
	
	private class RefinementPropertyModifyListener extends PropertyModifyHandler {

        @Override
		public void inputChanged(boolean hasError) {
			super.inputChanged(hasError);
			if (okButton != null)
				okButton.setEnabled(pdspecIsValid);
        }
		
	}
	
	@Override
	protected PropertyModifyHandler getPropertyModifyHandler() {
		return new RefinementPropertyModifyListener();
	}
    
    @Override
    protected Button createButton(Composite parent, int id, String label, boolean defaultButton) {
    	Button button = super.createButton(parent, id, label, defaultButton);
    	if (id == IDialogConstants.OK_ID)
            okButton.setEnabled(pdspecIsValid);
    	return button;
    }

    @Override
	protected void buttonPressed(int buttonId) {
        if (buttonId == IDialogConstants.OK_ID) {
            property = propertyDialog.computeProperty(pdspecInput.getText(), typeCache);
	    	worker.refine(this);
    	}
        super.buttonPressed(buttonId);
    }

    public void setExpressions(ArrayList<EvaluatedExpression> exprs) {
		results = exprs;
    }

}
