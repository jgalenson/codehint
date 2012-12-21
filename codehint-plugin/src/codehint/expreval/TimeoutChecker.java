package codehint.expreval;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.DebugException;
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

	public TimeoutChecker(IJavaThread thread, IJavaStackFrame stack, IJavaDebugTarget target, TypeCache typeCache) {
		super("Timeout checker");
		this.thread = thread;
		try {
			IJavaClassType exceptionType = (IJavaClassType)EclipseUtils.getTypeAndLoadIfNeeded("codehint.Timeout", stack, target, typeCache);
			if (exceptionType == null) {
				EclipseUtils.showError("Missing library", "Please add the codehint.CodeHintImpl library to the project's classpath.", null);
				throw new RuntimeException("Missing library codehint.CodeHintImpl");
			}
			this.exceptionObj = exceptionType.newInstance("()V", new IJavaValue[0], thread);
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
		this.isEvaluating = false;
		this.countField = null;
		this.lastCheckCount = 0;
	}
	
	public void startEvaluating(IJavaFieldVariable countField) {
		synchronized (this) {
			this.isEvaluating = true;
			this.countField = countField;
			this.lastCheckCount = -1;
		}
	}
	
	public void stopEvaluating() {
		synchronized (this) {
			this.isEvaluating = false;
			this.countField = null;
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
		try {
			thread.stop(exceptionObj);
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}

	public void start() {
		setPriority(Job.SHORT);
		schedule();
	}
	
	public void stop() {
		cancel();
	}
	
}
