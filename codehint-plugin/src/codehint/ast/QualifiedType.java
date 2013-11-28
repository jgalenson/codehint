package codehint.ast;

import org.eclipse.jdt.debug.core.IJavaType;

public class QualifiedType extends Type {

	private final Type qualifier;
	private final SimpleName name;
	
	public QualifiedType(IJavaType staticType, Type qualifier, SimpleName name) {
		super(staticType);
		this.qualifier = qualifier;
		this.name = name;
	}

	public Type getQualifier() {
		return qualifier;
	}

	public SimpleName getName() {
		return name;
	}
	
	@Override
	protected void accept0(ASTVisitor visitor) {
		boolean visitChildren = visitor.visit(this);
		if (visitChildren) {
			acceptChild(visitor, qualifier);
			acceptChild(visitor, name);
		}
	}
	
	@Override
	public String toString() {
		return qualifier.toString() + "." + name.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((qualifier == null) ? 0 : qualifier.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
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
		QualifiedType other = (QualifiedType) obj;
		if (qualifier == null) {
			if (other.qualifier != null)
				return false;
		} else if (!qualifier.equals(other.qualifier))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}

}
