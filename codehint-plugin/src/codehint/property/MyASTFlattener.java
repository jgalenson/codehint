package codehint.property;

import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.internal.core.dom.NaiveASTFlattener;

/**
 * Flatten an AST into a string.
 * This is currently just a wrapper around the internal
 * class that does the work, but it could be reimplemented.
 */
public class MyASTFlattener extends NaiveASTFlattener {
	
	@Override
	public String getResult() {
		return super.getResult();
	}
	
	protected void appendToBuffer(String s) {
		this.buffer.append(s);
	}
	
	@Override
	public boolean visit(MethodInvocation node) {
		return super.visit(node);
	}
	
	@Override
	public boolean visit(SimpleName node) {
		return super.visit(node);
	}

}
