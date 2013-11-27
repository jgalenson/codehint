package codehint.ast;

public abstract class ASTNode {
	
	private final int id;
	private static int idCounter = 0;
	
	public ASTNode() {
		this.id = idCounter++;
	}
	
	public int getID() {
		return id;
	}
	
	public final void accept(ASTVisitor visitor) {
		if (visitor.preVisit(this))
			accept0(visitor);  // dynamically dispatch to accept0 for type-specific visit
		visitor.postVisit(this);
	}
	
	protected abstract void accept0(ASTVisitor visitor);

	protected static void acceptChild(ASTVisitor visitor, ASTNode child) {
		if (child != null)
			child.accept(visitor);
	}

	protected static void acceptChildren(ASTVisitor visitor, ASTNode[] children) {
		if (children != null)
			for (ASTNode child: children)
				child.accept(visitor);
	}

}
