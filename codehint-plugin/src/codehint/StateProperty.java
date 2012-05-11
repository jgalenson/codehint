package codehint;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.internal.core.dom.NaiveASTFlattener;

public class StateProperty extends Property {
	
    private final static ASTParser parser = ASTParser.newParser(AST.JLS4);

	private final String lhs;
	private final Expression property;
	
	private StateProperty(String lhs, Expression property) {
		this.lhs = lhs;
		this.property = property;
	}
	
	public static StateProperty fromPropertyString(String lhs, String propertyStr) {
		Expression property = (Expression)EclipseUtils.parseExpr(parser, propertyStr);
		return new StateProperty(lhs, property);
	}
	
	public static String isLegalProperty(String str) {
		ASTNode property = EclipseUtils.parseExpr(parser, str);
    	if (property instanceof CompilationUnit)
    		return "Please enter a valid Java expression.";
    	return ValidityChecker.getError((Expression)property);
	}
	
	public Set<String> getPreVariables() {
		PreVariableFinder preFinder = new PreVariableFinder();
		property.accept(preFinder);
		return preFinder.getPreVariables();
	}

	/**
	 * Gets a string representing the evaluation of this
	 * property with lambda argument replaced by the given string.
	 * @param arg Actual argument.
	 * @return  string representing the evaluation of this
	 * property with lambda argument replaced by the given string.
	 */
	public String getReplacedString(String arg) {
		StateASTFlattener flattener = new StateASTFlattener(lhs, arg);
		property.accept(flattener);
		return flattener.getResult();
	}
	
	public static String getRenamedVar(String name) {
		return "_$pre_$" + name;
	}
	
	private static boolean isPre(MethodInvocation node) {
		return isPreOrPost(node, "pre");
	}
	
	private static boolean isPost(MethodInvocation node) {
		return isPreOrPost(node, "post");
	}
	
	private static boolean isPreOrPost(MethodInvocation node, String name) {
		return node.getExpression() instanceof SimpleName && "CodeHint".equals(((SimpleName)node.getExpression()).getIdentifier()) && name.equals(node.getName().getIdentifier());
	}
	
	@Override
	public String toString() {
		return property.toString();
	}
	
	private static class ValidityChecker extends ASTVisitor {
		
		private String error;
		
		private ValidityChecker() {
			error = null;
		}

    	@Override
    	public boolean visit(MethodInvocation node) {
    		if (isPre(node) || isPost(node))
	    		if (node.arguments().size() != 1 || !(node.arguments().get(0) instanceof SimpleName))
	    			error = "Call to " + node.getName() + " must take in only a variable, not " + node.arguments().get(0).toString();
    		return true;
    	}
    	
    	public static String getError(Expression property) {
    		ValidityChecker checker = new ValidityChecker();
    		property.accept(checker);
    		return checker.error;
    	}
		
	}
	
	private static class PreVariableFinder extends ASTVisitor {
		
		private final Set<String> preVariables;
		
		public PreVariableFinder() {
			preVariables = new HashSet<String>();
		}

		@Override
    	public boolean visit(MethodInvocation node) {
    		if (isPre(node))
	    		preVariables.add(((SimpleName)node.arguments().get(0)).getIdentifier());
    		return true;
    	}

    	public Set<String> getPreVariables() {
			return preVariables;
		}
		
	}
	
	private static final class StateASTFlattener extends NaiveASTFlattener {

		private final String lhs;
		private final String arg;
		
		public StateASTFlattener(String lhs, String arg) {
			super();
			this.lhs = lhs;
			this.arg = arg;
		}
		
		@Override
    	public boolean visit(MethodInvocation node) {
			if (isPre(node)) {
				this.buffer.append(getRenamedVar(((SimpleName)node.arguments().get(0)).getIdentifier()));
				return false;
			} else if (isPost(node)) {
				String nodeId = ((SimpleName)node.arguments().get(0)).getIdentifier();
				this.buffer.append(nodeId.equals(lhs) ? arg : nodeId);
				return false;
			} else
				return super.visit(node);
		}
		
	}

}
