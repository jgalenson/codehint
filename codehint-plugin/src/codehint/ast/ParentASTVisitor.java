package codehint.ast;

import java.util.Stack;

public class ParentASTVisitor extends ASTVisitor {
	
	protected Stack<ASTNode> parents;
	
	protected ParentASTVisitor() {
		parents = new Stack<ASTNode>();
	}

	@Override
	public boolean preVisit(ASTNode node) {
		parents.push(node);
		return true;
	}

	@Override
	public void postVisit(ASTNode node) {
		ASTNode p = parents.pop();
		assert p == node;
	}

}
