package codehint.effects;

import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.internal.debug.core.model.JDIThread;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.InvalidTypeException;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Value;

public class VarLVal extends LVal {
	
	private final LocalVariable var;
	private final ThreadReference thread;  // The StackFrame will be invalidated, so we instead store the thread.
	
	public VarLVal(String varName, IJavaThread thread) {
		try {
			this.thread = ((JDIThread)thread).getUnderlyingThread();
			this.var = this.thread.frame(0).visibleVariableByName(varName);
		} catch (IncompatibleThreadStateException e) {
			throw new RuntimeException(e);
		} catch (AbsentInformationException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Value getValue() {
		try {
			return thread.frame(0).getValue(var);
		} catch (IncompatibleThreadStateException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void setValue(Value value) throws InvalidTypeException, ClassNotLoadedException {
		try {
			thread.frame(0).setValue(var, value);
		} catch (IncompatibleThreadStateException e) {
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((var == null) ? 0 : var.name().hashCode());
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
		VarLVal other = (VarLVal) obj;
		if (var == null) {
			if (other.var != null)
				return false;
		} else if (!var.name().equals(other.var.name()))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return var.name();
	}

}
