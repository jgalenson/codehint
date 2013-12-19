package codehint.exprgen;

import codehint.ast.ASTVisitor;
import codehint.ast.ArrayAccess;
import codehint.ast.ClassInstanceCreation;
import codehint.ast.Expression;
import codehint.ast.FieldAccess;
import codehint.ast.InfixExpression;
import codehint.ast.MethodInvocation;
import codehint.ast.PrefixExpression;
import codehint.ast.SimpleName;

import com.sun.jdi.Field;
import com.sun.jdi.Method;

public class ProbabilityComputer extends ASTVisitor {
	
	private final ExpressionEvaluator expressionEvaluator;
	private final Weights weights;
	private double prob;
	private int count;
	
	private ProbabilityComputer(ExpressionEvaluator expressionEvaluator, Weights weights) {
		this.expressionEvaluator = expressionEvaluator;
		this.weights = weights;
		this.prob = 1d;
		this.count = 0;
	}
	
	public static double getProbability(Expression expr, ExpressionEvaluator expressionEvaluator, Weights weights) {
		return visit(expr, expressionEvaluator, weights).prob;
	}
	
	public static double getNormalizedProbability(Expression expr, ExpressionEvaluator expressionEvaluator, Weights weights) {
		ProbabilityComputer visitor = visit(expr, expressionEvaluator, weights);
		return visitor.prob / Math.pow(weights.getAverageWeight(), visitor.count);
	}
	
	private static ProbabilityComputer visit(Expression expr, ExpressionEvaluator expressionEvaluator, Weights weights) {
		ProbabilityComputer visitor = new ProbabilityComputer(expressionEvaluator, weights);
		expr.accept(visitor);
		return visitor;
	}

	@Override
	public boolean visit(MethodInvocation node) {
		return visitMethod(node);
	}

	@Override
	public boolean visit(ClassInstanceCreation node) {
		return visitMethod(node);
	}
	
	private boolean visitMethod(Expression node) {
		prob *= getMethodProbability(expressionEvaluator.getMethod(node), weights);
		count++;
		return true;
	}

	public static double getMethodProbability(Method method, Weights weights) {
		if (method == null)  // During refinement, we might not have an id.
			return weights.getAverageWeight();
		else
			return weights.getWeight(method.declaringType().name(), Weights.getMethodKey(method));
	}
	
	@Override
	public boolean visit(FieldAccess node) {
		return visitField(expressionEvaluator.getField(node));  // field is null for array length
	}
	
	@Override
	public boolean visit(SimpleName node) {
		Field field = expressionEvaluator.getField(node);
		if (field != null && field.isPublic())  // Don't count unqualified non-public field accesses.
			return visitField(field);
		return true;
	}
	
	private boolean visitField(Field field) {
		prob *= getFieldProbability(field, weights);
		count++;
		return true;
	}
	
	public static double getFieldProbability(Field field, Weights weights) {
		if (field == null)
			return weights.getAverageWeight();
		else
			return weights.getWeight(field.declaringType().name(), field.name()) * weights.getFieldWeight();  // We bias fields by how often they occur compared to methods: P(field) / P(method).
	}
	
	@Override
	public boolean visit(InfixExpression node) {
		return visitAverage();
	}
	
	@Override
	public boolean visit(PrefixExpression node) {
		return visitAverage();
	}
	
	@Override
	public boolean visit(ArrayAccess node) {
		return visitAverage();
	}
	
	private boolean visitAverage() {
		prob *= weights.getAverageWeight();
		count++;
		return true;
	}
	
}
