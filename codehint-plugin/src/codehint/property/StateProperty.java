package codehint.property;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaVariable;
import org.eclipse.jdt.debug.eval.IAstEvaluationEngine;

import codehint.ast.ASTConverter;
import codehint.ast.ASTNode;
import codehint.ast.ASTVisitor;
import codehint.ast.CompilationUnit;
import codehint.ast.Expression;
import codehint.ast.FieldAccess;
import codehint.ast.MethodInvocation;
import codehint.ast.ParentASTFlattener;
import codehint.ast.ParentASTVisitor;
import codehint.ast.SimpleName;
import codehint.utils.EclipseUtils;

/**
 * Note that variables without primes default to pre.
 */
public class StateProperty extends Property {

	public static final String FREE_VAR_NAME = "_rv";
	
    private final static ASTParser parser = ASTParser.newParser(AST.JLS4);

	private final String lhs;
	private final Expression property;
	private final String propertyStr;
	private final Set<String> preVariables;
	private final Set<String> postVariables;
	private final boolean usesLHS;
	/*private final Map<Integer, IJavaValue> preNodeValues;
	private final Set<Integer> postNodes;
	private final ArrayList<Expression> freeVars;*/
	
	protected StateProperty(String lhs, Expression property, String propertyStr, IJavaStackFrame stack) {
		this.lhs = lhs;
		this.property = property;
		this.propertyStr = propertyStr;
		PrePostVariableFinder prePostFinder = new PrePostVariableFinder(lhs, stack);
		property.accept(prePostFinder);
		this.preVariables = prePostFinder.getPreVariables();
		this.postVariables = prePostFinder.getPostVariables();
		this.usesLHS = prePostFinder.usesLHS();
		/*this.preNodeValues = prePostFinder.getPreNodes();
		this.postNodes = prePostFinder.getPostNodes();
		this.freeVars = prePostFinder.getFreeVars();*/
	}
	
	public static StateProperty fromPropertyString(String lhs, String propertyStr, IJavaStackFrame stack) {
		Expression property = (Expression)rewritePrimeSyntax(propertyStr);
		//property = (Expression)ASTConverter.parseExprWithTypes(parser, property.toString());
		return new StateProperty(lhs, property, propertyStr, stack);
	}
	
	public static String isLegalProperty(String str, IJavaStackFrame stackFrame, IAstEvaluationEngine evaluationEngine) {
    	if ("".equals(str))
    		return "Please enter a property.";
		ASTNode property = rewritePrimeSyntax(str);
		if (property instanceof CompilationUnit)
			return "Enter a valid expression: " + ((CompilationUnit)property).getProblems()[0].getMessage();
		String compileErrors = EclipseUtils.getCompileErrors(property.toString().replaceAll("CodeHint.post", ""), "boolean", stackFrame, evaluationEngine);
		if (compileErrors != null)
			return compileErrors;
		return ValidityChecker.getError((Expression)property);
	}
	
	private static ASTNode rewritePrimeSyntax(String str) {
		while (true) {
			ASTNode node = ASTConverter.parseExpr(parser, str);
	    	if (node instanceof CompilationUnit) {
	    		IProblem problem = ((CompilationUnit)node).getProblems()[0];
	    		int problemStart = problem.getSourceStart();
	    		if (problem.getID() == IProblem.InvalidCharacterConstant && problemStart < str.length() && str.charAt(problemStart) == '\'') {
	    			str = rewriteSinglePrime(str, problemStart);
	    		} else
	    			return node;
	    	} else
	    		return node;
		}
		
	}

	public static String rewriteSinglePrime(String str, int problemStart) {
		int varStart = problemStart - 1;
		while (varStart >= 0 && Character.isJavaIdentifierPart(str.charAt(varStart)))
			varStart--;
		str = str.substring(0, varStart + 1) + "CodeHint.post(" + str.substring(varStart + 1, problemStart) + ")" + str.substring(problemStart + 1);
		return str;
	}
	
	public Set<String> getPreVariables() {
		return preVariables;
	}
	
	public Set<String> getPostVariables() {
		return postVariables;
	}
	
	@Override
	public boolean usesLHS() {
		return usesLHS;
	}

	/**
	 * Gets a string representing the evaluation of this
	 * property with lambda argument replaced by the given string.
	 * @param arg Actual argument.
	 * @return  string representing the evaluation of this
	 * property with lambda argument replaced by the given string.
	 */
	@Override
	public String getReplacedString(String arg, IJavaStackFrame stack) {
		return (new StateASTFlattener(lhs, arg, stack)).getResult(property);
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
	
	public String getPropertyString() {
		return propertyStr;
	}
	
	@Override
	public String toString() {
		return propertyStr;
	}
	
	private static class ValidityChecker extends ASTVisitor {
		
		private String error;
		
		private ValidityChecker() {
			error = null;
		}

    	@Override
    	public boolean visit(MethodInvocation node) {
    		if (isPre(node) || isPost(node)) {
	    		if (node.arguments().length != 1)
	    			error = "Call to " + node.getName() + " must take in only a variable, not " + Arrays.toString(node.arguments());
	    		else if (!(node.arguments()[0] instanceof SimpleName))
	    			error = "Call to " + node.getName() + " must take in only a variable, not " + node.arguments()[0].toString();
    		}
    		return true;
    	}
    	
    	public static String getError(Expression property) {
    		ValidityChecker checker = new ValidityChecker();
    		property.accept(checker);
    		return checker.error;
    	}
		
	}
	
	private static class PrePostVariableFinder extends ParentASTVisitor {

		private final String lhs;
		private final IJavaStackFrame stack;
		private final Set<String> preVariables;
		private final Set<String> postVariables;
		private boolean usesLHS;
		/*private final Map<Integer, IJavaValue> preNodeValues;
		private final Set<Integer> postNodes;
		private final ArrayList<Expression> freeVars;*/
		
		public PrePostVariableFinder(String lhs, IJavaStackFrame stack) {
			this.lhs = lhs;
			this.stack = stack;
			this.preVariables = new HashSet<String>();
			this.postVariables = new HashSet<String>();
			this.usesLHS = false;
			/*this.preNodeValues = new HashMap<Integer, IJavaValue>();
			this.postNodes = new HashSet<Integer>();
			this.freeVars = new ArrayList<Expression>();*/
		}

		@Override
    	public boolean visit(MethodInvocation node) {
			if (isPre(node)) {
				String varName = ((SimpleName)node.arguments()[0]).getIdentifier();
				preVariables.add(varName);
				/*IJavaVariable var = stack.findVariable(varName);
				preNodeValues.put(node.getID(), (IJavaValue)var.getValue());
				node.setStaticType(var.getJavaType());*/
				return false;
			} else if (isPost(node)) {
				String varName = ((SimpleName)node.arguments()[0]).getIdentifier();
				postVariables.add(varName);
				usesLHS = usesLHS || varName.equals(lhs);
				/*postNodes.add(node.getID());
				IJavaVariable var = stack.findVariable(varName);
				assert var != null || varName.equals(FREE_VAR_NAME);
				if (var == null)
					freeVars.add(node);
				else
					node.setStaticType(var.getJavaType());*/
				return false;
			}
			return true;
    	}
		
		@Override
		public boolean visit(SimpleName node) {
			try {
				if (parentIsName(node))
					return true;  // Ignore names.
				IJavaVariable var = stack.findVariable(node.getIdentifier());
				if (var != null) {
					preVariables.add(node.getIdentifier());
					/*preNodeValues.put(node.getID(), (IJavaValue)var.getValue());
					node.setStaticType(var.getJavaType());*/
				}
			} catch (DebugException e) {
				throw new RuntimeException(e);
			}
			return true;
		}

    	public Set<String> getPreVariables() {
			return preVariables;
		}

    	public Set<String> getPostVariables() {
			return postVariables;
		}
    	
    	public boolean usesLHS() {
    		return usesLHS;
    	}
    	
    	/*public Map<Integer, IJavaValue> getPreNodes() {
    		return preNodeValues;
    	}
    	
    	public Set<Integer> getPostNodes() {
    		return postNodes;
    	}

		public ArrayList<Expression> getFreeVars() {
			return freeVars;
		}*/
		
	}
	
	private static final class StateASTFlattener extends ParentASTFlattener {

		private final String lhs;
		private final String arg;
		private final IJavaStackFrame stack;
		
		public StateASTFlattener(String lhs, String arg, IJavaStackFrame stack) {
			this.lhs = lhs;
			this.arg = arg;
			this.stack = stack;
		}
		
		@Override
    	protected void flatten(MethodInvocation node, StringBuilder sb) {
			if (isPre(node)) {
				sb.append(getRenamedVar(((SimpleName)node.arguments()[0]).getIdentifier()));
			} else if (isPost(node)) {
				String nodeId = ((SimpleName)node.arguments()[0]).getIdentifier();
				sb.append(nodeId.equals(lhs) ? arg : nodeId);
			} else
				super.flatten(node, sb);
		}
		
		@Override
		protected void flatten(SimpleName node, StringBuilder sb) {
			try {
				if (parentIsName(node))
					super.flatten(node, sb);
				else if (stack.findVariable(node.getIdentifier()) != null)
					sb.append(getRenamedVar(node.getIdentifier()));
				else
					super.flatten(node, sb);
			} catch (DebugException e) {
				throw new RuntimeException(e);
			}
		}
		
	}

	/*@Override
	public boolean meetsSpecification(Expression expression, ExpressionEvaluator expressionEvaluator) {
		for (Expression freeVar: freeVars)  // The static type of free variables changes, since it is that of the type of the expression, so we must reset it each time.
			freeVar.resetStaticType(expression.getStaticType());
		Result exprResult = expressionEvaluator.getResult(expression, Collections.<Effect>emptySet());
		System.out.println(expression);
		Result javaResult = expressionEvaluator.evaluateExpressionWithEffects(property, exprResult.getEffects(), null, new StatePropertyEvaluator(exprResult.getValue().getValue()));
		System.out.println(" Got " + javaResult);
		try {
			return javaResult != null && !"V".equals(javaResult.getValue().getValue().getSignature()) && ((IJavaPrimitiveValue)javaResult.getValue().getValue()).getBooleanValue();
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}
	
	public class StatePropertyEvaluator {
		
		private final IJavaValue newLhsVarVal;

		public StatePropertyEvaluator(IJavaValue newLhsVarVal) {
			this.newLhsVarVal = newLhsVarVal;
		}

		public IJavaValue getPropertyValue(Expression expr, Set<Effect> effects, IJavaStackFrame stack) {
			if (preNodeValues.containsKey(expr.getID()))
				return preNodeValues.get(expr.getID());  // Use the cached value of the pre node.
			else if (postNodes.contains(expr.getID())) {
				try {
					String varName = ((SimpleName)((MethodInvocation)expr).arguments()[0]).getIdentifier();
					if (varName.equals(lhs))
						return newLhsVarVal;  // Use the value of the synthesized expression.
					try {
						SideEffectHandler.redoEffects(effects);  // Use the current value of the variable.
						return (IJavaValue)stack.findVariable(varName).getValue();
					} finally {
						SideEffectHandler.undoEffects(effects);
					}
				}catch (DebugException e) {
					throw new RuntimeException(e);
				} 
			} else
				return null;
		}

	}*/

}
