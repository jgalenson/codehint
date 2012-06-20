package codehint;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.IMarkerResolution;
import org.eclipse.ui.IMarkerResolutionGenerator2;

import codehint.property.StateProperty;
import codehint.utils.EclipseUtils;

public class PrimeFixer implements IMarkerResolutionGenerator2 {

	@Override
	public IMarkerResolution[] getResolutions(IMarker marker) {
		if (hasResolutions(marker))
			return new IMarkerResolution[] { new PrimeFix() };
		else
			return new IMarkerResolution[0];
	}

	@Override
	public boolean hasResolutions(IMarker marker) {
		try {
			Object problemStartAttribute = marker.getAttribute(IMarker.CHAR_START);
			if (problemStartAttribute != null) {
				IDocument document = EclipseUtils.getDocument();
				int problemStart = ((Integer)problemStartAttribute).intValue();
				return "\'".equals(document.get(problemStart, 1)) && Character.isJavaIdentifierPart(document.get(problemStart - 1, 1).charAt(0)) && marker.getAttribute(IMarker.LINE_NUMBER) != null;
			}
		} catch (Exception e) { }
		return false;
	}
	
	private static class PrimeFix implements IMarkerResolution {

		@Override
		public String getLabel() {
			return "Wrap in CodeHint.post().";
		}

		@Override
		public void run(IMarker marker) {
			try {
				IDocument document = EclipseUtils.getDocument();
				int problemStart = ((Integer)marker.getAttribute(IMarker.CHAR_START)).intValue();
				int lineNumber = ((Integer)marker.getAttribute(IMarker.LINE_NUMBER)).intValue() - 1;
				int lineOffset = document.getLineOffset(lineNumber);
				int lineLength = document.getLineLength(lineNumber);
				String curLine = document.get(lineOffset, lineLength);
				String newLine = StateProperty.rewriteSinglePrime(curLine, problemStart - lineOffset);
				document.replace(lineOffset, lineLength, newLine);
			} catch (CoreException e) {
				e.printStackTrace();
			} catch (BadLocationException e) {
				e.printStackTrace();
			}
		}
		
	}

}
