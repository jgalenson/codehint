package codehint.expreval;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.debug.core.IJavaArray;
import org.eclipse.jdt.debug.core.IJavaClassType;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaFieldVariable;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaPrimitiveValue;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaValue;

import codehint.exprgen.TypeCache;
import codehint.utils.EclipseUtils;

public class TimeoutChecker extends Job {
	
	public static final long TIMEOUT_TIME_MS = 1000l;
	
	private final IJavaThread thread;
	private final IJavaObject exceptionObj;
	private boolean isEvaluating;
	private IJavaFieldVariable countField;
	private int lastCheckCount;
	private boolean killed;
	
	private final IJavaClassType awtWindowType;
	private final IJavaValue[] awtWindows;
	private final IJavaClassType swtDisplayType;
	private final IJavaValue swtDefaultDisplay;
	private final IJavaValue[] swtShells;

	public TimeoutChecker(IJavaThread thread, IJavaStackFrame stack, IJavaDebugTarget target, TypeCache typeCache) {
		super("Timeout checker");
		this.thread = thread;
		try {
			IJavaClassType exceptionType = EclipseUtils.loadLibrary("codehint.Timeout", stack, target, typeCache);
			this.exceptionObj = exceptionType.newInstance("()V", new IJavaValue[0], thread);
			
			// TODO: Getting the types here will fail if the corresponding classes have not yet been loaded but will be loaded during some evaluation.  This seems unlikely (most GUI apps have probably already started the GUI), so we ignore it for efficiency.  If we wanted, we could replace this with calls to the versions that load the type.
			awtWindowType = (IJavaClassType)EclipseUtils.getFullyQualifiedTypeIfExists("java.awt.Window", stack, target, typeCache);
			if (awtWindowType == null)
				awtWindows = null;
			else
				awtWindows = getAWTWindows();
			swtDisplayType = (IJavaClassType)EclipseUtils.getFullyQualifiedTypeIfExists("org.eclipse.swt.widgets.Display", stack, target, typeCache);
			if (swtDisplayType == null) {
				swtDefaultDisplay = null;
				swtShells = null;
			} else {
				swtDefaultDisplay = swtDisplayType.sendMessage("getDefault", "()Lorg/eclipse/swt/widgets/Display;", new IJavaValue[] { }, thread);
				swtShells = getSWTShells();
			}
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
		this.isEvaluating = false;
		this.countField = null;
		this.lastCheckCount = 0;
		this.killed = false;
	}

	private IJavaValue[] getAWTWindows() throws DebugException {
		return ((IJavaArray)awtWindowType.sendMessage("getWindows", "()[Ljava/awt/Window;", new IJavaValue[] { }, thread)).getValues();
	}

	private IJavaValue[] getSWTShells() throws DebugException {
		if (swtDefaultDisplay.isNull())
			return null;
		return ((IJavaArray)((IJavaObject)swtDefaultDisplay).sendMessage("getShells", "()[Lorg/eclipse/swt/widgets/Shell;", new IJavaValue[] { }, thread, null)).getValues();
	}
	
	public void startEvaluating(IJavaFieldVariable countField) {
		synchronized (this) {
			this.isEvaluating = true;
			this.countField = countField;
			this.lastCheckCount = -1;
			this.killed = false;
		}
	}
	
	public void stopEvaluating() {
		synchronized (this) {
			this.isEvaluating = false;
			this.countField = null;
			cleanupWindows();
		}
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		//System.out.println("Checking for timeout");
		if (!monitor.isCanceled()) {
			synchronized (this) {
				int count = 1;
				try {
					if (countField != null)
						count = ((IJavaPrimitiveValue)countField.getValue()).getIntValue();
				} catch (DebugException e) {
					throw new RuntimeException(e);
				}
				if (isEvaluating && lastCheckCount == count) {
					stopThread();
					isEvaluating = false;
				}
				lastCheckCount = count;
			}
			schedule(TIMEOUT_TIME_MS);
		}
		return Status.OK_STATUS;
	}
	
	// TODO: This does not stop a thread if it's stuck in a loop.  But this is true of Java's Thread.stop, so I don't think the VM can actually stop a thread.
	private void stopThread() {
		//System.out.println("Timeout");
		this.killed = true;
		try {
			thread.stop(exceptionObj);
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}
	
	private void cleanupWindows() {
		if (!killed)  // We only cleanup windows if the evaluation timed out, as otherwise opened windows were probably already closed.
			return;
		try {
			if (awtWindows != null) {
				IJavaValue[] newWindows = getAWTWindows();
				for (IJavaValue newWindow: newWindows) {
					if (!contains(awtWindows, newWindow)) {
						//System.out.println("Killing AWT window " + newWindow);
						((IJavaObject)newWindow).sendMessage("dispose", "()V", new IJavaValue[] { }, thread, null);
					}
				}
			}
			if (swtShells != null) {
				IJavaValue[] newShells = getSWTShells();
				for (IJavaValue newShell: newShells) {
					if (!contains(swtShells, newShell)) {
						//System.out.println("Killing SWT shell " + newShell);
						((IJavaObject)newShell).sendMessage("dispose", "()V", new IJavaValue[] { }, thread, null);
						// Without the update message the shells won't actually close.
						((IJavaObject)swtDefaultDisplay).sendMessage("update", "()V", new IJavaValue[] { }, thread, null);
					}
				}
			}
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}
	
	private static boolean contains(IJavaValue[] arr, IJavaValue target) {
		for (IJavaValue elem: arr)
			if (target.equals(elem))
				return true;
		return false;
	}

	public void start() {
		setPriority(Job.SHORT);
		schedule();
	}
	
	public void stop() {
		cancel();
	}
	
}
