package codehint.ast;

import org.eclipse.jdt.debug.core.IJavaType;

public class DoubleLiteral extends NumberLiteral {
	
	private final double number;
	
	public DoubleLiteral(IJavaType staticType, double n) {
		super(staticType);
		this.number = n;
	}

	public double getNumber() {
		return number;
	}
	
	@Override
	protected void accept0(ASTVisitor visitor) {
		visitor.visit(this);
	}
	
	@Override
	public String toString() {
		return String.valueOf(number);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		long temp;
		temp = Double.doubleToLongBits(number);
		result = prime * result + (int) (temp ^ (temp >>> 32));
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
		DoubleLiteral other = (DoubleLiteral) obj;
		if (Double.doubleToLongBits(number) != Double
				.doubleToLongBits(other.number))
			return false;
		return true;
	}

}
