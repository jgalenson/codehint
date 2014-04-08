package codehint.ast;

import java.util.Stack;

public class ParentASTFlattener extends ASTFlattener {
	
	protected final Stack<ASTNode> parents;
	
	protected ParentASTFlattener() {
		parents = new Stack<ASTNode>();
	}
	
	@Override
	protected void flatten(Expression node, StringBuilder sb) {
		parents.push(node);
		super.flatten(node, sb);
		ASTNode n = parents.pop();
		assert node == n;
	}
	
	protected boolean parentIsName(ASTNode node) {
		ASTNode parent = parents.size() == 1 ? null : parents.elementAt(parents.size() - 2);
		return parent != null && ((parent instanceof MethodInvocation && ((MethodInvocation)parent).getName() == node) || (parent instanceof FieldAccess && ((FieldAccess)parent).getName() == node));
	}

}
