package codehint.expreval;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.debug.core.IJavaArray;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.IJavaValue;
import org.eclipse.jdt.debug.eval.IAstEvaluationEngine;
import org.eclipse.jdt.debug.eval.ICompiledExpression;
import org.eclipse.jdt.debug.eval.IEvaluationListener;
import org.eclipse.jdt.debug.eval.IEvaluationResult;

import codehint.EclipseUtils;
import codehint.exprgen.TypedExpression;
import codehint.property.StateProperty;
import codehint.property.Property;

/**
 * Class for evaluating expressions.
 */
public class EvaluationManager {
	
	private final static int BATCH_SIZE = 100;
	private final static int MIN_NUM_BATCHES = 4;
	
	public static class EvaluationError extends RuntimeException {
		
		private static final long serialVersionUID = 1L;

		public EvaluationError(String msg) {
			super(msg);
		}
		
	}

	/**
	 * Evaluates the given expressions.
	 * If the desired property is non-null, it returns those that satisfy it;
	 * otherwise, it returns all those whose execution does not crash.
	 * @param exprs The expressions to evaluate
	 * @param stack The current stack frame.
	 * @param property The desired property, or null if there is none.
	 * @param monitor a progress monitor, or null if progress reporting and cancellation are not desired.
     * @return the results of the evaluations of the given expressions
     * that satisfy the given property, if it is non-null.
	 */
	public static ArrayList<EvaluatedExpression> evaluateExpressions(ArrayList<TypedExpression> exprs, IJavaStackFrame stack, Property property, IProgressMonitor monitor) {
		try {
			IAstEvaluationEngine engine = EclipseUtils.getASTEvaluationEngine(stack);
			int batchSize = exprs.size() >= 2 * BATCH_SIZE ? BATCH_SIZE : exprs.size() >= MIN_NUM_BATCHES ? exprs.size() / MIN_NUM_BATCHES : 1;
			String validVal = property == null ? "true" : property.getReplacedString("_$curValue", stack);
			Map<String, ArrayList<TypedExpression>> expressionsByType = getExpressionByType(exprs);
			ArrayList<EvaluatedExpression> evaluatedExprs = new ArrayList<EvaluatedExpression>(exprs.size());
			for (Map.Entry<String, ArrayList<TypedExpression>> expressionsOfType: expressionsByType.entrySet())
				evaluatedExprs.addAll(evaluateExpressions(expressionsOfType.getValue(), EclipseUtils.sanitizeTypename(expressionsOfType.getKey()), engine, stack, property, validVal, -1, batchSize, monitor));
			return evaluatedExprs;
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Splits the given expressions by their type, either object or primitives.
	 * We do this so that we can evaluate as many expressions together as possible
	 * by storing them into a shared array.  If we used an array of objects for
	 * everything, we would box primitives, which modifies them.  So we put all objects
	 * in an array of objects and primitives in arrays of their own.
	 */
	private static Map<String, ArrayList<TypedExpression>> getExpressionByType(ArrayList<TypedExpression> exprs) throws DebugException {
		Map<String, ArrayList<TypedExpression>> expressionsByType = new HashMap<String, ArrayList<TypedExpression>>();
		for (TypedExpression expr: exprs) {
			IJavaType type = expr.getType();
			String typeName = type == null ? null : EclipseUtils.isObject(type.getSignature()) ? "java.lang.Object" : type.getName();
			if (!expressionsByType.containsKey(typeName))
				expressionsByType.put(typeName, new ArrayList<TypedExpression>());
			expressionsByType.get(typeName).add(expr);
		}
		// Nulls have a null type, so put them with the objects.
		if (expressionsByType.containsKey(null)) {
			ArrayList<TypedExpression> nulls = expressionsByType.remove(null);
			if (!expressionsByType.containsKey("java.lang.Object"))
				expressionsByType.put("java.lang.Object", new ArrayList<TypedExpression>());
			expressionsByType.get("java.lang.Object").addAll(nulls);
		}
		return expressionsByType;
	}

	/**
	 * Filters the given evaluated expressions and keeps only
	 * those that satisfy the given property.
	 * @param evaledExprs The expressions to filter
	 * @param target The debug target.
	 * @param stack The current stack frame.
	 * @param type The static type of the desired expression.
	 * @param property The desired property.
	 * @param monitor a progress monitor, or null if progress reporting and cancellation are not desired.
     * @return the evaluated expressions that satisfy
     * the given property.
	 */
	public static ArrayList<EvaluatedExpression> filterExpressions(ArrayList<EvaluatedExpression> evaledExprs, IJavaStackFrame stack, Property property) {
		ArrayList<TypedExpression> exprs = new ArrayList<TypedExpression>(evaledExprs.size());
		for (EvaluatedExpression expr : evaledExprs)
			exprs.add(new TypedExpression(expr.getExpression(), expr.getType(), null));
		return evaluateExpressions(exprs, stack, property, new NullProgressMonitor());
	}
	
	/**
	 * Evaluates the given expressions.
	 * @param exprs The expressions to evaluate
	 * @param engine The evaluation engine.
	 * @param stack The current stack frame.
	 * @param type The static type of the desired expression.
	 * @param property The desired property, or null if there is none.
	 * @param startIndex The index at which to start evaluation.
	 * If this is -1, we evaluate everything that cannot throw an exception.
	 * Otherwise, we evaluate batchSize things at once (evaluating them
	 * sequentially/slowly if that fails) and then continue to the next
	 * batchSize elements.
	 * @param batchSize The size of the batches.
     * @return the results of the evaluations of the given expressions.
	 */
	private static ArrayList<EvaluatedExpression> evaluateExpressions(ArrayList<TypedExpression> exprs, String type, IAstEvaluationEngine engine, IJavaStackFrame stack, Property property, String validVal, int startIndex, int batchSize, IProgressMonitor monitor) {
		if (monitor.isCanceled())
			throw new OperationCanceledException();
		ArrayList<TypedExpression> evaluatedExpressions = startIndex != -1 ? null : new ArrayList<TypedExpression>();
		ArrayList<TypedExpression> unevaluatedExpressions = startIndex != -1 ? null : new ArrayList<TypedExpression>();
		// Make the string that evaluates everything.
		// If evaluateCanErrors is true, this includes everything; otherwise, it only contains those that cannot throw exceptions.
		// TODO: If the user has variables with the same names as the ones I introduce, this will crash....
		int numExprsToEvaluate = 0;
		StringBuilder expressionsStr = new StringBuilder();
		
		try {
			if (property instanceof StateProperty)
				for (String preVar: ((StateProperty)property).getPreVariables(stack))
					expressionsStr.append(stack.findVariable(preVar).getJavaType().getName() + " " + StateProperty.getRenamedVar(preVar) + " = " + preVar + ";\n");
			
			int i = startIndex == -1 ? 0 : startIndex; 
	    	for (; i < exprs.size() && (startIndex == -1 || numExprsToEvaluate < batchSize); i++) {
	    		TypedExpression curExpr = exprs.get(i);
	    		PreconditionFinder pf = new PreconditionFinder();
	    		curExpr.getExpression().accept(pf);
	    		String preconditions = pf.getPreconditions();
	    		if (startIndex == -1 && pf.canThrowException()) {  // If we do not want to immediately evaluate things that can throw exceptions, collect them and do them later.
	    			unevaluatedExpressions.add(curExpr);
	    			continue;
	    		}
	    		//String legalStr = "_$legal[" + numExprsToEvaluate + "] = true";
	    		String valueStr = type + " _$curValue = " + curExpr.toString();
	    		String valueLHS = "_$value[" + numExprsToEvaluate + "]";
	    		String validLHS = "_$valid[" + numExprsToEvaluate + "]";
	    		String body = "{\n " + valueStr + ";\n " + validLHS + " = " + validVal + ";\n " + valueLHS + " = _$curValue;\n}\n";
	    		if (preconditions.length() > 0)
	    			expressionsStr.append("if (" + preconditions + ") ");
				expressionsStr.append(body);
				if (startIndex == -1)
					evaluatedExpressions.add(curExpr);
	    		numExprsToEvaluate++;
	    	}
	    	String newTypeString = "[" + numExprsToEvaluate + "]";
            if (type.contains("[]")) {  // If this is an array type, we must specify our new size as the first array dimension, not the last one.
                int index = type.indexOf("[]");
                newTypeString = type.substring(0, index) + newTypeString + type.substring(index); 
            } else
                newTypeString = type + newTypeString;
	    	//String legalDecl = "boolean[] _$legal = new boolean[" + numExprsToEvaluate + "];\n";
			expressionsStr.insert(0, "{\n" + type + "[] _$value = new " + newTypeString + ";\nboolean[] _$valid = new boolean[" + numExprsToEvaluate + "];\n");
	    	expressionsStr.append("return new Object[] { _$value, _$valid };\n}");
	    	
			// Evaluate things that cannot throw exceptions (and things that can if evaluateCanErrors is true).
	    	//if (startIndex == -1) System.out.println("Doing " + numExprsToEvaluate + " quickly with startIndex=" + startIndex + ".");
	    	ICompiledExpression compiled = engine.getCompiledExpression(expressionsStr.toString(), stack);
	    	if (compiled.hasErrors())  // The user entered a property that does not compile, so notify them.
	    		throw new EvaluationError("Evaluation error: " + "The following errors were encountered during evaluation.\nDid you enter a valid property?\n\n" + EclipseUtils.getCompileErrors(compiled));
	    	IEvaluationResult evaluationResult = Evaluator.evaluateExpression(compiled, engine, stack);
    		//if (startIndex == -1) System.out.println("Just did " + numExprsToEvaluate + " quickly with startIndex=" + startIndex + ".");
	    	ArrayList<EvaluatedExpression> results;
	    	if (startIndex == -1) {  // If we do not want to try to evaluate things that can throw exceptions.
	    		if (evaluationResult.hasErrors()) {  // Evaluating a property threw an exception.  We notify the user and discard those inputs.
	    			EclipseUtils.showWarning("Evaluation error", "Evaluation " + (property == null ? "" : "of property\n\t" + property.toString() + "\n") + "crashed with the following error:\n\t" + EclipseUtils.getErrors(evaluationResult) + "\nThis might be fine, so we're continuing.", null);
	    			int step = batchSize >= exprs.size() ? 1 : batchSize;
	    			results = evaluateExpressionsInBatches(exprs, type, engine, stack, property, validVal, 0, exprs.size(), step, monitor);
	    		} else {
	    			results = getResultsFromArray(evaluatedExpressions, 0, evaluationResult);
	    			monitor.worked(numExprsToEvaluate);
	    			if (unevaluatedExpressions.size() > 0)  // Evaluate (in batches) things that can throw.
	    				results.addAll(evaluateExpressionsInBatches(unevaluatedExpressions, type, engine, stack, property, validVal, 0, unevaluatedExpressions.size(), batchSize, monitor));
	    		}
	    	} else {  // If we do want to try to evaluate things that can throw exceptions.
	    		if (evaluationResult.hasErrors()) {  // If evaluation threw an exception, we must re-evaluate them all sequentially/slowly.
	    			if (numExprsToEvaluate > 1) {  // Batch evaluation failed, so evaluate sequentially.
	    				//System.out.println("Batch evaluation failed.");
	    				results = evaluateExpressionsInBatches(exprs, type, engine, stack, property, validVal, startIndex, i, 1, monitor);
	    			} else { // The one expression crashed, so ignore it.
    					//System.err.println("Evaluation of " + exprs.get(i-1) + " failed with error " + EclipseUtils.getErrors(evaluationResult));
	    				results = new ArrayList<EvaluatedExpression>(0);
		    			monitor.worked(1);
	    			}
	    		} else { // Nothing threw an exception, so we can use the result.
	    			results = getResultsFromArray(exprs, startIndex, evaluationResult);
	    			monitor.worked(numExprsToEvaluate);
	    		}
	    	}
	    	return results;
		} catch (DebugException ex) {
			ex.printStackTrace();
			throw new RuntimeException("Debug exception.");
		}
	}

	/**
	 * Evaluates the given expressions in batches
	 * @param exprs The expressions to evaluate
	 * @param engine The evaluation engine.
	 * @param stack The current stack frame.
	 * @param type The static type of the desired expression.
	 * @param property The desired property, or null if there is none.
	 * @param startIndex The index at which to start evaluation.
	 * @param endIndex The index at which to end evaluation (exclusive).
	 * @param batchSize The size of the batches.
     * @return the results of the evaluations of the given expressions.
	 */
	private static ArrayList<EvaluatedExpression> evaluateExpressionsInBatches(ArrayList<TypedExpression> exprs, String type, IAstEvaluationEngine engine, IJavaStackFrame stack, Property property, String validVal, int startIndex, int endIndex, int batchSize, IProgressMonitor monitor) {
		ArrayList<EvaluatedExpression> results = new ArrayList<EvaluatedExpression>();
		//System.out.println("Evaluating " + (endIndex - startIndex) + " expressions in batches of " + batchSize + ".");
		for (int i = startIndex; i < endIndex; i += batchSize)
			results.addAll(evaluateExpressions(exprs, type, engine, stack, property, validVal, i, batchSize, monitor));
		//System.out.println("Just did " + (endIndex - startIndex) + " expressions in batches of " + batchSize + ".");
		return results;
	}
	
	/**
	 * Gets the evaluated expressions of those expressions
	 * whose execution did not crash and that produce a
	 * valid result.  See evaluateExpressions for the
	 * string on whose evaluation this is called.
	 * @param exprs The original expressions.
	 * @param startIndex The index in the list of expressions
	 * where the evaluated started.
	 * @param evaluationResult The result of the evaluation.
	 * @return the evaluated expressions of those expressions
	 * whose execution did not crash.
	 * @throws DebugException a DebugException occurs.
	 */
	private static ArrayList<EvaluatedExpression> getResultsFromArray(ArrayList<TypedExpression> exprs, int startIndex, IEvaluationResult evaluationResult) throws DebugException {
		IJavaValue[] resultValue = ((IJavaArray)evaluationResult.getValue()).getValues();
		IJavaValue[] resultValues = ((IJavaArray)resultValue[0]).getValues();
		IJavaValue[] validValues = ((IJavaArray)resultValue[1]).getValues();
		ArrayList<EvaluatedExpression> results = new ArrayList<EvaluatedExpression>();
		for (int i = 0; i < resultValues.length; i++)
			if (/*"true".equals(legalValues[i].getValueString()) && */"true".equals(validValues[i].getValueString())) {
				TypedExpression typedExpr = exprs.get(startIndex + i);
				results.add(new EvaluatedExpression(typedExpr.getExpression(), resultValues[i], typedExpr.getType()));
			}
		return results;
	}
	
	private static class Evaluator {
		
		private static final Semaphore semaphore = new Semaphore(0);
		private static final IEvaluationResult[] results = new IEvaluationResult[] { null };
		private static final EvaluationListener evaluationListener = new EvaluationListener(results, semaphore);
	
		/**
		 * Evaluates the given expression synchronously.
		 * @param compiled The compiled expression to evaluate.
		 * @param engine The evaluation engine.
		 * @param stack The current stack frame.
		 * @return The result of the evaluation.
		 */
	    public /*synchronized*/ static IEvaluationResult evaluateExpression(ICompiledExpression compiled, IAstEvaluationEngine engine, IJavaStackFrame stack) {
			try {
				engine.evaluateExpression(compiled, stack, evaluationListener, DebugEvent.EVALUATION_IMPLICIT, false);
				semaphore.acquire();
				return results[0];
			} catch (InterruptedException ex) {
				ex.printStackTrace();
				throw new RuntimeException("Thread interrupted.");
			} catch (DebugException ex) {
				ex.printStackTrace();
				throw new RuntimeException("Debug Exception.");
			}
	    }
	    
	    /**
	     * Class to wait for evaluations to finish.
	     */
	    private static class EvaluationListener implements IEvaluationListener {
	
	    	private final IEvaluationResult[] results;
	    	private final Semaphore semaphore;
	    	
	    	public EvaluationListener(IEvaluationResult[] results, Semaphore semaphore) {
	    		this.results = results;
	    		this.semaphore = semaphore;
	    	}
	
			@Override
			public void evaluationComplete(IEvaluationResult result) {
				results[0] = result;
				semaphore.release();  // Up the semaphore to mark that the evaluation finished.
			}
	    	
	    }
	    
	}
    
    /**
     * Finds the preconditions under which we can evaluate the
     * given expression without it crashing, or notes that we
     * can never be sure of this.
     */
    private static class PreconditionFinder extends ASTVisitor {
    	
    	private String preconditions;
    	private boolean canThrowException;
    	
    	public PreconditionFinder() {
    		preconditions = "";
    		canThrowException = false;
    	}
    	
    	// a[i] -> a != null && i >= 0 && i < a.length 
    	@Override
    	public boolean visit(ArrayAccess node) {
    		String arrStr = node.getArray().toString();
    		String indexStr = node.getIndex().toString();
    		add("(" + arrStr + " != null && " + indexStr + " >= 0 && " + indexStr + " < " + arrStr + ".length)");
    		return true;
    	}
    	
    	// p.q -> p != null
    	@Override
    	public boolean visit(FieldAccess node) {
    		/*if (node.getExpression().getProperty("isStatic") == null)  // Ignore static field accesses like Test.foo.  But this won't work since at refinement time we don't have this information.
    			add(node.getExpression() + " != null");*/
    		if ((IJavaValue)node.getProperty("value") != null)
    			return true;  // The node can be evaluated safely because we already computed its value (so it must have one).
    		canThrowException = true;
    		IJavaValue exprValue = (IJavaValue)node.getExpression().getProperty("value");
    		if (exprValue != null && exprValue.isNull())
    			add("false");  // We know the expression is null and this will crash.
    		return true;
    	}
    	
    	// x/y -> y != 0
    	// x%y -> y != 0
    	@Override
    	public boolean visit(InfixExpression node) {
    		if (node.getOperator() == InfixExpression.Operator.DIVIDE || node.getOperator() == InfixExpression.Operator.REMAINDER)
    			add(node.getRightOperand() + " != 0");
    		return true;
    	}
    	
    	// x.foo() -> x != null
    	@Override
    	public boolean visit(MethodInvocation node) {
    		/*if (node.getExpression() != null && node.getExpression().getProperty("isStatic") == null && !(node.getExpression() instanceof QualifiedName))  // If the expression is a Qualified name, it is a package name and not a variable.  But this won't work since at refinement time we don't have this information.
    			add(node.getExpression() + " != null");*/
    		if ((IJavaValue)node.getProperty("value") != null)
    			return true;  // The node can be evaluated safely because we already computed its value (so it must have one).
    		canThrowException = true;
    		IJavaValue exprValue = node.getExpression() == null ? null : (IJavaValue)node.getExpression().getProperty("value");
    		if (exprValue != null && exprValue.isNull())
    			add("false");  // We know the expression is null and this will crash.
    		return true;
    	}
    	
    	// Constructor calls can always throw and we can't (currently) statically evaluate them.
    	@Override
		public boolean visit(ClassInstanceCreation node) {
    		canThrowException = true;
    		return true;
    	}
    	
    	private void add(String str) {
    		if (preconditions.length() == 0)
    			preconditions = str;
    		else
    			preconditions = str + " && " + preconditions;
    	}
    	
    	/**
    	 * Checks whether this expression can throw an exception
    	 * even if the precondition is true.
    	 * @return whether this expression can throw an exception
    	 * even if the precondition is true.
    	 */
    	public boolean canThrowException() {
    		return canThrowException;
    	}
    	
    	/**
    	 * Finds the precondition under which the evaluation
    	 * of this expression will not throw an exception
    	 * (if canThrowException is false).
    	 * @return the precondition under which the evaluation
    	 * of this expression will not throw an exception
    	 * (if canThrowException is false).
    	 */
    	public String getPreconditions() {
    		return preconditions;
    	}
    	
    }

}