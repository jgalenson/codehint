package codehint.dialogs;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.eval.IAstEvaluationEngine;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.swt.widgets.Shell;

import codehint.utils.EclipseUtils;

public abstract class ValuePropertyDialog extends SynthesisDialog {
	
	protected final String varName;
	private final String pdspecMessage;
	private final String initialPdspecText;
	private final IInputValidator pdspecValidator;
    
    public ValuePropertyDialog(String varName, String varTypeName, IJavaStackFrame stack, Shell shell, String initialValue, String extraMessage, boolean getSkeleton) {
    	super(shell, varName, varTypeName, stack, getSkeleton);
    	this.varName = varName;
    	String pdspecMessage = "Demonstrate an expression for " + varName + ".  We will find expressions that evaluate to the same value.";
    	this.pdspecMessage = getFullMessage(pdspecMessage, extraMessage);
    	this.initialPdspecText = initialValue;
    	this.pdspecValidator = new ExpressionValidator(stack, varTypeName);
    }

	@Override
	protected String getPdspecMessage() {
    	return pdspecMessage;
	}

	@Override
	protected String getInitialPdspecText() {
		return initialPdspecText;
	}

	@Override
	protected IInputValidator getPdspecValidator() {
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
	protected String getHelpID() {
		return "value";
	}

}
