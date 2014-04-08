package codehint.ast;

import java.util.Arrays;


public class Block extends Statement {
	
	private final Statement[] statements;
	
	public Block(Statement[] statements) {
		this.statements = statements;
	}
	
	public Statement[] statements() {
		return statements;
	}

	@Override
	protected void accept0(ASTVisitor visitor) {
		boolean visitChildren = visitor.visit(this);
		if (visitChildren)
			acceptChildren(visitor, this.statements);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("{\n");
		sb.append(getBodyString());
		if (statements.length > 0)
			sb.append("\n");
		sb.append("}");
		return sb.toString();
	}

	public String getBodyString() {
		int iMax = statements.length - 1;
		if (iMax == -1)
			return "";
		StringBuilder sb = new StringBuilder();
		for (int i = 0; ; i++) {
			sb.append(String.valueOf(statements[i])).append(";");
			if (i == iMax)
				break;
			else if (statements[i] instanceof Expression)
				sb.append("\n");
		}
		return sb.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(statements);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Block other = (Block) obj;
		if (!Arrays.equals(statements, other.statements))
			return false;
		return true;
	}

}
