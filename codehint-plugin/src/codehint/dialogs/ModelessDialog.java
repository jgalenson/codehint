package codehint.dialogs;

import org.eclipse.jface.dialogs.TrayDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * A modeless dialog box.
 * Adapted from http://www.eclipse.org/forums/index.php/mv/tree/4336/.
 * TODO: Using this allows the user to do some weird things like continue execution, which can potentially do weird things.
 */
public class ModelessDialog extends TrayDialog {

	protected ModelessDialog(Shell parentShell) {
		super(parentShell);
		setBlockOnOpen(false);
	}

	@Override
	protected void setShellStyle(int newShellStyle)
	{
		int newstyle = newShellStyle & ~SWT.APPLICATION_MODAL;
		newstyle |= SWT.MODELESS;
		super.setShellStyle(newstyle);
	}

	@Override
	public int open()
	{
		int retVal = super.open();
		pumpMessages();
		return retVal; // TODO: Since super.open() now returns immediately, we don't get the real return value.  Specifically, if the user clicks Cancel, we think they clicked OK.
	}

	protected void pumpMessages()
	{
		Shell shell = getShell();
		Display display = shell.getDisplay();
		while (!shell.isDisposed())
			if (!display.readAndDispatch())
				display.sleep();
		display.update();
	}

}
