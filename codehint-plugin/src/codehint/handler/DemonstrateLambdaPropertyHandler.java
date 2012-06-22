package codehint.handler;

import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.IJavaVariable;
import org.eclipse.swt.widgets.Shell;

import codehint.Synthesizer;
import codehint.dialogs.LambdaPropertyDialog;
import codehint.dialogs.SynthesisDialog;
import codehint.property.LambdaProperty;
import codehint.property.Property;
import codehint.property.ValueProperty;
import codehint.utils.EclipseUtils;

public class DemonstrateLambdaPropertyHandler extends CommandHandler {
    
    @Override
	public void handle(IVariable variable, String path, Shell shell) {
    	try {
	    	Property lastCrashedProperty = Synthesizer.getLastCrashedProperty(path);
	    	String initValue = lastCrashedProperty instanceof LambdaProperty && !(lastCrashedProperty instanceof ValueProperty) ? lastCrashedProperty.toString() : "";
	    	IJavaType varStaticType = ((IJavaVariable)variable).getJavaType();
	    	String varTypeName = EclipseUtils.sanitizeTypename(varStaticType.getName());
	    	SynthesisDialog dialog = new LambdaPropertyDialog(path, varTypeName, varStaticType, shell, initValue, null, true);
	    	Synthesizer.synthesizeAndInsertExpressions(variable, path, dialog, shell, false);
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
    }

}
