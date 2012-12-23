package codehint.expreval;

import java.util.HashMap;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.debug.core.IJavaClassType;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaValue;
import org.eclipse.jdt.internal.debug.core.breakpoints.JavaMethodBreakpoint;
import org.eclipse.jdt.internal.debug.core.model.JDIDebugTarget;
import org.eclipse.jdt.internal.debug.core.model.JDIThread;
import codehint.exprgen.TypeCache;
import codehint.utils.EclipseUtils;

import com.sun.jdi.Method;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.LocatableEvent;

// TODO: Optimize this code.  All method breakpoints perform equally slowly, so I would have to detect when I don't need them (e.g., no loaded non-standard native methods with checking on class load).
// TODO: When this is enabled it sometimes causes a hang/timeout even when it doesn't kill anything.  The hangs I've seen are in Swing, I think because something is trying and failing to acquire a lock.
public class NativeHandler {

	private boolean enabled;
	private final IJavaObject exceptionObj;
	private MyNativeBreaker nativeBreakpoint;
	
	public NativeHandler(IJavaThread thread, IJavaStackFrame stack, IJavaDebugTarget target, TypeCache typeCache) {
		try {
			this.enabled = true;
			IJavaClassType exceptionType = EclipseUtils.loadLibrary("codehint.NativeCall", stack, target, typeCache);
			exceptionObj = exceptionType.newInstance("()V", new IJavaValue[0], thread);
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}
	
	private static class MyNativeBreaker extends JavaMethodBreakpoint {
		
		private final IJavaObject exceptionObj;
		
		public MyNativeBreaker(IJavaObject exceptionObj) throws CoreException {
			super(ResourcesPlugin.getWorkspace().getRoot(), "*", null, null, true, false, true, -1, -1, -1, 0, true, new HashMap<String, Object>(10));
			this.exceptionObj = exceptionObj;
		}
		
		@Override
		protected boolean handleMethodEvent(LocatableEvent event, Method method, JDIThread thread, boolean suspendVote) {
			//System.out.println("Checking " + method.declaringType().name() + "." + method.name());
			if (!method.isNative())
				return true;
			//System.out.println("Really checking " + method.declaringType().name() + "." + method.name());
			String name = method.declaringType().name();
			if (!name.startsWith("java.") && !name.startsWith("sun.")) {
				//System.out.println("Killing " + method.declaringType().name() + "." + method.name());
				try {
					thread.stop(exceptionObj);
				} catch (DebugException e) {
					throw new RuntimeException(e);
				}
			}
			return true;
		}
		
		@Override
		public void eventSetComplete(Event event, JDIDebugTarget target, boolean suspend, EventSet eventSet) {
			// Avoid doing the superclass work for efficiency.
		}
		
	}
	
	public void blockNativeCalls() {
		if (!enabled)
			return;
		try {
			nativeBreakpoint = new MyNativeBreaker(exceptionObj);
		} catch (CoreException e) {
			throw new RuntimeException(e);
		}
	}
	
	public void allowNativeCalls() {
		try {
			if (nativeBreakpoint != null) {
				nativeBreakpoint.delete();
				nativeBreakpoint = null;
			}
		} catch (CoreException e) {
			throw new RuntimeException(e);
		}
	}

	public void enable(boolean enable) {
		enabled = enable;
	}

}
