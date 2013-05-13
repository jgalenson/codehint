package codehint.dialogs;

import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.debug.core.IJavaArray;
import org.eclipse.jdt.debug.core.IJavaPrimitiveValue;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaValue;
import org.eclipse.jdt.debug.eval.IAstEvaluationEngine;
import org.eclipse.jface.dialogs.IInputValidator;

import codehint.expreval.EvaluationManager.EvaluationError;
import codehint.exprgen.TypeCache;
import codehint.property.ArrayValueProperty;
import codehint.property.ObjectValueProperty;
import codehint.property.PrimitiveValueProperty;
import codehint.property.Property;
import codehint.utils.EclipseUtils;

public class ValuePropertyDialog extends PropertyDialog {

	protected final IJavaStackFrame stack;
	private final String pdspecMessage;
	private final String initialPdspecText;
	private final IInputValidator pdspecValidator;
    
    public ValuePropertyDialog(String varName, String varTypeName, IJavaStackFrame stack, String initialValue, String extraMessage) {
    	super(varName, extraMessage);
    	this.stack = stack;
    	String pdspecMessage = "Demonstrate an expression for " + varName + ".  We will find expressions that evaluate to the same value.";
    	this.pdspecMessage = getFullMessage(pdspecMessage, extraMessage);
    	this.initialPdspecText = initialValue;
    	this.pdspecValidator = new ExpressionValidator(stack, varTypeName);
    }

	@Override
	public String getPdspecMessage() {
    	return pdspecMessage;
	}

	@Override
	public String getInitialPdspecText() {
		return initialPdspecText;
	}

	@Override
	public IInputValidator getPdspecValidator() {
		return pdspecValidator;
	}

    private static class ExpressionValidator implements IInputValidator {
    	
        private final static ASTParser parser = ASTParser.newParser(AST.JLS4);
        private final IJavaStackFrame stackFrame;
        private final IAstEvaluationEngine evaluationEngine;
        private final String varTypeName;
        
        public ExpressionValidator(IJavaStackFrame stackFrame, String varTypeName) {
        	this.stackFrame = stackFrame;
        	this.evaluationEngine = EclipseUtils.getASTEvaluationEngine(stackFrame);
        	this.varTypeName = varTypeName;
        }
        
        @Override
		public String isValid(String newText) {
        	if ("".equals(newText))
        		return "Please enter a Java expression.";
        	ASTNode node = EclipseUtils.parseExpr(parser, newText);
        	if (node instanceof CompilationUnit)
        		return "Enter a valid expression: " + ((CompilationUnit)node).getProblems()[0].getMessage();
			String compileErrors = EclipseUtils.getCompileErrors(newText, varTypeName, stackFrame, evaluationEngine);
			if (compileErrors != null)
				return compileErrors;
        	return null;
        }
    }

	@Override
	public Property computeProperty(String propertyText, TypeCache typeCache) {
		if (propertyText == null)
			return null;
		else {
    		try {
		    	IJavaValue demonstrationValue = EclipseUtils.evaluate(propertyText, stack);
		    	if (demonstrationValue instanceof IJavaPrimitiveValue)
		    		return PrimitiveValueProperty.fromPrimitive(EclipseUtils.javaStringOfValue(demonstrationValue, stack, false), demonstrationValue);
		    	else if (demonstrationValue instanceof IJavaArray)
	    			return ArrayValueProperty.fromArray(propertyText, demonstrationValue);
		    	else
		    		return ObjectValueProperty.fromObject(propertyText, demonstrationValue);
    		} catch (EvaluationError e) {
				throw e;
			} catch (DebugException e) {
				throw new RuntimeException(e);
			}
		}
	}

	@Override
	public String getHelpID() {
		return "value";
	}

}
