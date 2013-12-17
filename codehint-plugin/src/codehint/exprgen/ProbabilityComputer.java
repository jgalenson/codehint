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
	
	private ProbabilityComputer(ExpressionEvaluator expressionEvaluator, Weights weights) {
		this.expressionEvaluator = expressionEvaluator;
		this.weights = weights;
		this.prob = 1;
	}
	
	public static double getProbability(Expression expr, ExpressionEvaluator expressionEvaluator, Weights weights) {
		ProbabilityComputer visitor = new ProbabilityComputer(expressionEvaluator, weights);
		expr.accept(visitor);
		return visitor.prob;
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
		Method method = expressionEvaluator.getMethod(node);
		if (method == null)  // During refinement, we might not have an id.
			prob *= weights.getAverageWeight();
		else
			prob *= weights.getWeight(method.declaringType().name(), Weights.getMethodKey(method));
		return true;
	}
	
	@Override
	public boolean visit(FieldAccess node) {
		Field field = expressionEvaluator.getField(node);
		if (field == null)  // Array length
			prob *= weights.getAverageWeight();
		else
			visitField(field);
		return true;
	}
	
	@Override
	public boolean visit(SimpleName node) {
		Field field = expressionEvaluator.getField(node);
		if (field != null && field.isPublic())  // Don't count unqualified non-public field accesses.
			visitField(field);
		return true;
	}
	
	private void visitField(Field field) {
		if (field != null) {
			prob *= weights.getWeight(field.declaringType().name(), field.name());
			// We bias fields by how often they occur compared to methods: P(field) / P(method).
			prob *= weights.getFieldWeight();  // TODO: Use the actually-computed value once I update the weights file to add it.
		}
	}
	
	@Override
	public boolean visit(InfixExpression node) {
		prob *= weights.getAverageWeight();
		return true;
	}
	
	@Override
	public boolean visit(PrefixExpression node) {
		prob *= weights.getAverageWeight();
		return true;
	}
	
	@Override
	public boolean visit(ArrayAccess node) {
		prob *= weights.getAverageWeight();
		return true;
	}
	
}
