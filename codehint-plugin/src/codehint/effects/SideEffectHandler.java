package codehint.effects;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.debug.core.IJavaArray;
import org.eclipse.jdt.debug.core.IJavaClassPrepareBreakpoint;
import org.eclipse.jdt.debug.core.IJavaMethodEntryBreakpoint;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaValue;
import org.eclipse.jdt.internal.debug.core.breakpoints.JavaClassPrepareBreakpoint;
import org.eclipse.jdt.internal.debug.core.breakpoints.JavaMethodEntryBreakpoint;
import org.eclipse.jdt.internal.debug.core.breakpoints.JavaWatchpoint;
import org.eclipse.jdt.internal.debug.core.model.JDIDebugTarget;
import org.eclipse.jdt.internal.debug.core.model.JDIObjectValue;
import org.eclipse.jdt.internal.debug.ui.BreakpointUtils;

import codehint.utils.EclipseUtils;
import codehint.utils.MutablePair;
import codehint.utils.Pair;

import com.sun.jdi.ArrayReference;
import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.ClassType;
import com.sun.jdi.Field;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.InvalidTypeException;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StackFrame;
import com.sun.jdi.StringReference;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Value;
import com.sun.jdi.event.AccessWatchpointEvent;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.event.ModificationWatchpointEvent;

public class SideEffectHandler {
	
	private final IJavaStackFrame stack;
	private final IJavaProject project;
	private long maxID;
	
	public SideEffectHandler(IJavaStackFrame stack, IJavaProject project) {
		this.stack = stack;
		this.project = project;
	}
	
	// TODO: Call this in the constructor to reduce the slowness?  If so, change Synthesizer so it doesn't disable or double-delete these breakpoints.  Also ensure we always delete these breakpoints.
	public void start(IProgressMonitor monitor) {
		if (!enabled)
			return;
		SubMonitor curMonitor = SubMonitor.convert(monitor, "Side effect handler setup", IProgressMonitor.UNKNOWN);
		List<IField> fields = getAllLoadedMutableFields(stack, project, curMonitor);
		curMonitor.setWorkRemaining(fields.size());
		addedWatchpoints = getFieldWatchpoints(fields);
		curMonitor.worked(fields.size());
		try {
			prepareBreakpoint = new MyClassPrepareBreakpoint(project);
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
		reflectionBreakpoints = getReflectionBreakpoints(project);
		collectionDisableds = new ArrayList<ObjectReference>();
		curMonitor.done();
	}

	private static List<ReferenceType> getAllLoadedTypes(IJavaStackFrame stack) {
		//long startTime = System.currentTimeMillis();
		List<ReferenceType> internalTypes = ((JDIDebugTarget)stack.getDebugTarget()).getVM().allClasses();
		List<ReferenceType> types = new ArrayList<ReferenceType>(internalTypes.size());
		for (ReferenceType type: internalTypes) {
			if (!type.name().contains("[]"))
				types.add(type);
		}
		//System.out.println("types: " + (System.currentTimeMillis() - startTime));
		return types;
	}
	
	private List<IField> getAllLoadedMutableFields(IJavaStackFrame stack, IJavaProject project, SubMonitor monitor) {
		List<ReferenceType> loadedTypes = getAllLoadedTypes(stack);
		monitor.setWorkRemaining(loadedTypes.size());
		//long startTime = System.currentTimeMillis();
		long maxID = Long.MIN_VALUE;
		List<IField> fields = new ArrayList<IField>();
		for (ReferenceType type: loadedTypes) {
			try {
				IType itype = project.findType(type.name());
				if (itype == null) {
					//System.out.println("Bad times on " + type.name());
					continue;
				}
				if (itype.getPackageFragment().getElementName().equals("codehint")
						|| itype.getFullyQualifiedName().equals("java.lang.String")  // A String's value array will never change and we don't care about its hashCode.
						|| itype.getFullyQualifiedName().equals("java.lang.Class"))
					continue;
				List<ObjectReference> instances = null;
				for (IField field: itype.getFields()) {
					if (!Flags.isFinal(field.getFlags()) || canBeArray(field)) {
						if (Flags.isStatic(field.getFlags()))
							fields.add(field);
						else {
							if (instances == null) {
								instances = type.instances(Long.MAX_VALUE);
								for (ObjectReference instance: instances) {
									long id = instance.uniqueID();
									if (id > maxID)
										maxID = id;
								}
								// Abstract classes seem to return no instances, so check their subclasses, but don't bother checking for maxID.  (Without this, we don't get AbstractList.modCount, which makes toString on subList fail.)
								if (instances.isEmpty() && type.isAbstract()) {
									for (ClassType subtype: ((ClassType)type).subclasses()) {
										instances = subtype.instances(1);
										if (!instances.isEmpty())
											break;
									}
								}
							}
							if (!instances.isEmpty())
								fields.add(field);
						}
					}
				}
			} catch (JavaModelException e) {
				//System.out.println("Bad times on " + type.name());
				continue;  // Calling getFields() on some anonymous classes throws an exception....
			} finally {
				monitor.worked(1);
			}
		}
		this.maxID = maxID;
		//System.out.println("fields: " + (System.currentTimeMillis() - startTime));
		/*for (IField field: fields)
			System.out.println(field);*/
		return fields;
	}
	
	private static boolean canBeArray(IField field) throws JavaModelException {
		String typeSig = field.getTypeSignature();
		return typeSig.contains("[") || typeSig.equals("Ljava.lang.Object;") || typeSig.equals("QObject;");
	}
	
	private List<MyJavaWatchpoint> getFieldWatchpoints(final Collection<IField> fields) {
		try {
			final List<MyJavaWatchpoint> watchpoints = new ArrayList<MyJavaWatchpoint>(fields.size());
			IWorkspaceRunnable wr = new IWorkspaceRunnable() {
				@Override
				public void run(IProgressMonitor monitor) throws CoreException {
					for (IField field: fields)
						watchpoints.add(new MyJavaWatchpoint(field));
					DebugPlugin.getDefault().getBreakpointManager().addBreakpoints(watchpoints.toArray(new IBreakpoint[watchpoints.size()]));
				}
			};
			//long startTime = System.currentTimeMillis();
			if (!fields.isEmpty())
				ResourcesPlugin.getWorkspace().run(wr, null);
			//System.out.println("run: " + (System.currentTimeMillis() - startTime));
			//System.out.println("Installed " + watchpoints.size() + " breakpoints.");
			return watchpoints;
		} catch (CoreException ex) {
			throw new RuntimeException(ex);
		}
	}
	
	private List<IJavaMethodEntryBreakpoint> getReflectionBreakpoints(IJavaProject project) {
		try {
			List<IJavaMethodEntryBreakpoint> reflectionBreakpoints = new ArrayList<IJavaMethodEntryBreakpoint>();
			// We don't need to instrument to Array methods because they need to read the array, so we will already catch them.
			IType fieldType = project.findType("java.lang.reflect.Field");
			reflectionBreakpoints.add(new ReflectionBreakpoint(fieldType.getMethod("set", new String[] { "Ljava.lang.Object;", "Ljava.lang.Object;" })));
			reflectionBreakpoints.add(new ReflectionBreakpoint(fieldType.getMethod("setBoolean", new String[] { "Ljava.lang.Object;", "Z" })));
			reflectionBreakpoints.add(new ReflectionBreakpoint(fieldType.getMethod("setByte", new String[] { "Ljava.lang.Object;", "B" })));
			reflectionBreakpoints.add(new ReflectionBreakpoint(fieldType.getMethod("setChar", new String[] { "Ljava.lang.Object;", "C" })));
			reflectionBreakpoints.add(new ReflectionBreakpoint(fieldType.getMethod("setShort", new String[] { "Ljava.lang.Object;", "S" })));
			reflectionBreakpoints.add(new ReflectionBreakpoint(fieldType.getMethod("setInt", new String[] { "Ljava.lang.Object;", "I" })));
			reflectionBreakpoints.add(new ReflectionBreakpoint(fieldType.getMethod("setLong", new String[] { "Ljava.lang.Object;", "J" })));
			reflectionBreakpoints.add(new ReflectionBreakpoint(fieldType.getMethod("setFloat", new String[] { "Ljava.lang.Object;", "F" })));
			reflectionBreakpoints.add(new ReflectionBreakpoint(fieldType.getMethod("setDouble", new String[] { "Ljava.lang.Object;", "D" })));
			reflectionBreakpoints.add(new ReflectionBreakpoint(fieldType.getMethod("get", new String[] { "Ljava.lang.Object;" })));
			return reflectionBreakpoints;
		} catch (CoreException e) {
			throw new RuntimeException(e);
		}
	}
	
	private class MyJavaWatchpoint extends JavaWatchpoint {
		
		private final boolean canBeArray;
		private final boolean isFinal;
		
		public MyJavaWatchpoint(IField field) throws CoreException {
			//super(BreakpointUtils.getBreakpointResource(field.getDeclaringType()), field.getDeclaringType().getElementName(), field.getElementName(), -1, -1, -1, 0, false, new HashMap<String, Object>(10));
			
			canBeArray = canBeArray(field);
			isFinal = Flags.isFinal(field.getFlags());

			IType type = field.getDeclaringType();
			IResource resource = BreakpointUtils.getBreakpointResource(field.getDeclaringType());
			String typeName = type.getFullyQualifiedName();
			String fieldName = field.getElementName();
			Map<String, Object> attributes = new HashMap<String, Object>(10);
			
			// Copied from JavaWatchpoint's constructor.
			setMarker(resource.createMarker(JavaWatchpoint.JAVA_WATCHPOINT));
			addLineBreakpointAttributes(attributes, getModelIdentifier(), true, -1, -1, -1);
			addTypeNameAndHitCount(attributes, typeName, 0);
			attributes.put(SUSPEND_POLICY, new Integer(getDefaultSuspendPolicy()));
			addFieldName(attributes, fieldName);
			addDefaultAccessAndModification(attributes);
			ensureMarker().setAttributes(attributes);
		}

		@Override
		protected boolean[] getDefaultAccessAndModificationValues() {
			return new boolean[] { canBeArray, !isFinal };  // We do not want to break on accesses to non-array fields or modifications to final fields.
		}

		@Override
		public boolean handleEvent(Event event, JDIDebugTarget target, boolean suspendVote, EventSet eventSet) {
			if (effectsMap == null)  // We're not currently tracking side effects.
				return true;
			if (event instanceof ModificationWatchpointEvent) {
				ModificationWatchpointEvent modEvent = (ModificationWatchpointEvent)event;
				if (modEvent.location().method().isStaticInitializer())
					return true;  // If the field is modified in a static initializer, it must be the initialization of a static field of a newly-loaded class, which we don't want to revert.
				ObjectReference obj = modEvent.object();
				if (obj != null && obj.uniqueID() > maxID) {
					//System.out.println("Ignoring new object " + obj.toString());
					return true;
				}
				recordEffect(FieldLVal.makeFieldLVal(obj, modEvent.field()), modEvent.valueCurrent(), modEvent.valueToBe());
				return true;
			} else if (event instanceof AccessWatchpointEvent) {
				AccessWatchpointEvent readEvent = (AccessWatchpointEvent)event;
				Field field = readEvent.field();
				if (readEvent.valueCurrent() instanceof ArrayReference) {
					ObjectReference obj = readEvent.object();
					if (obj != null && obj.uniqueID() > maxID) {
						//System.out.println("Ignoring new object " + obj.toString());
						return true;
					}
					backupArray(FieldLVal.makeFieldLVal(obj, field), readEvent.valueCurrent());
					return true;
				}
				return true;
			}
			return super.handleEvent(event, target, suspendVote, eventSet);
		}
		
	}

	private void recordEffect(FieldLVal lval, Value oldVal, Value newVal) {
		if (oldVal != newVal && (oldVal == null || !oldVal.equals(newVal))) {
			if (readArrays.contains(lval))  // If the static type of the field is Object, we might track it twice, in write and array read.
				return;
			changedFields.add(lval);
			RVal oldRVal = RVal.makeRVal(oldVal);
			RVal newRVal = RVal.makeRVal(newVal);
			if (!effectsMap.containsKey(lval)) {
				effectsMap.put(lval, new MutablePair<RVal, RVal>(oldRVal, newRVal));
				disableCollection(oldVal);
			} else {
				MutablePair<RVal, RVal> oldEffect = effectsMap.get(lval);
				if (oldEffect.first.equals(newRVal)) {
					effectsMap.remove(lval);
					enableCollection(oldVal);
				} else
					oldEffect.second = newRVal;
			}
			//System.out.println("Changing " + lval.toString() + " from " + oldVal + " to " + newVal);
		}/* else
			System.out.println("Unchanged " + lval.toString() + " from " + oldVal);*/
	}

	private void backupArray(FieldLVal lval, Value oldVal) {
		if (changedFields.contains(lval))  // If the static type of the field is Object, we might track it twice, in write and array read.
			return;
		readArrays.add(lval);
		ArrayValue oldRVal = ArrayValue.makeArrayValue((ArrayReference)oldVal);
		if (!readFieldsMap.containsKey(lval)) {
			readFieldsMap.put(lval, oldRVal);
			disableCollection(oldVal);
			//System.out.println("Reading " + lval.toString() + " from " + (oldVal == null ? "null" : getValues((ArrayReference)oldVal)));
		}
	}
	
	private Set<Effect> getEffects() {
		Set<Effect> effects = new HashSet<Effect>();
		for (Map.Entry<FieldLVal, MutablePair<RVal, RVal>> entry: effectsMap.entrySet()) {
			// We need to update the new rval if it's an array because its entries might have changed.
			Effect effect = new Effect(entry.getKey(), entry.getValue().first, updateRVal(entry.getValue().second));
			effects.add(effect);
		}
		for (Map.Entry<FieldLVal, ArrayValue> entry: readFieldsMap.entrySet()) {
			LVal lval = entry.getKey();
			ArrayValue initialVal = entry.getValue();
			Value newVal = lval.getValue();
			Effect effect = new Effect(lval, initialVal, RVal.makeRVal(newVal));
			if (newVal instanceof ArrayReference) {
				ArrayReference newValArray = (ArrayReference)newVal;
				if (!initialVal.equals(newValArray))
					effects.add(effect);
				else {
					//System.out.println("Unchanged " + lval.toString() + " from " + initialVal);
					enableCollection(initialVal.getValue());
				}
			} else
				effects.add(effect);
		}
		// We do arg arrs last, since they might have already been handled by one of the above mechanisms (e.g., passing a field as an array).  I could try to optimize it so that I know if such a case has happened and avoid the extra equality check.
		for (Pair<ArrayReference, ArrayValue> info: argArrs) {
			if (!info.second.equals(info.first)) {
				Effect effect = new Effect(new ArgArrLVal(info.first), info.second, RVal.makeRVal(info.first));
				effects.add(effect);
			} else {
				enableCollection(info.first);
				//System.out.println("Unchanged arg arr from " + info.second);*/
			}
		}
		for (Effect effect: effects) {
			disableCollection(effect.getNewVal().getValue());
			storeCollectionDisableds(effect.getOldVal().getValue());
			storeCollectionDisableds(effect.getNewVal().getValue());
		}
		/*for (Effect effect: effects)
			System.out.println("Effect: " + effect);*/
		return effects;
	}
	
	public static Set<Effect> undoEffects(Set<Effect> effects) {
		try {
			for (Effect effect: effects) {
				//System.out.println("Resetting " + effect);
				effect.undo();
			}
			return effects;
		} catch (InvalidTypeException e) {
			throw new RuntimeException(e);
		} catch (ClassNotLoadedException e) {
			throw new RuntimeException(e.className(), e);
		}
	}
	
	private static RVal updateRVal(RVal rval) {
		if (rval instanceof ArrayValue)
			return RVal.makeRVal(rval.getValue());
		else
			return rval;
	}
	
	// Work around a bug where getValues() crashes when called on an empty array.
	protected static List<Value> getValues(ArrayReference value) {
		if (value.length() == 0)
			return new ArrayList<Value>(0);
		else
			return value.getValues();
	}
	
	// Dealing with array values, which can be nested.
	
	private class MyClassPrepareBreakpoint extends JavaClassPrepareBreakpoint {
		
		private final IJavaProject project;
		
		public MyClassPrepareBreakpoint(IJavaProject project) throws DebugException {
			super(ResourcesPlugin.getWorkspace().getRoot(), "*", IJavaClassPrepareBreakpoint.TYPE_CLASS, -1, -1, true, new HashMap<String, Object>(10));
			this.project = project;
		}
		
		@Override
		public boolean handleClassPrepareEvent(ClassPrepareEvent event, JDIDebugTarget target, boolean suspendVote) {
			//System.out.println("Prepare " + event.referenceType());
			try {
				IType itype = project.findType(event.referenceType().name());
				List<IField> fields = new ArrayList<IField>();
				if (itype == null) {
					//System.out.println("Bad times on " + event.referenceType().name());
				} else {
					for (IField field: itype.getFields())
						if ((!Flags.isFinal(field.getFlags()) || field.getTypeSignature().contains("[")) && Flags.isStatic(field.getFlags()))
							fields.add(field);
					List<MyJavaWatchpoint> newWatchpoints = getFieldWatchpoints(fields);
					addedWatchpoints.addAll(newWatchpoints);
				}
			} catch (JavaModelException e) {
				throw new RuntimeException(e);
			}
			return super.handleClassPrepareEvent(event, target, suspendVote);
		}
		
	}
	
	private boolean enabled;
	
	private List<MyJavaWatchpoint> addedWatchpoints;
	private MyClassPrepareBreakpoint prepareBreakpoint;
	private List<IJavaMethodEntryBreakpoint> reflectionBreakpoints;
	
	private Map<FieldLVal, MutablePair<RVal, RVal>> effectsMap;
	private Map<FieldLVal, ArrayValue> readFieldsMap;
	private List<Pair<ArrayReference, ArrayValue>> argArrs;
	private Set<FieldLVal> changedFields;
	private Set<FieldLVal> readArrays;
	private Set<ArrayReference> backedUpArrays;
	
	public void startHandlingSideEffects() {
		if (!enabled)
			return;
		if (effectsMap == null) {
			effectsMap = new HashMap<FieldLVal, MutablePair<RVal, RVal>>();
			readFieldsMap = new HashMap<FieldLVal, ArrayValue>();
			argArrs = new ArrayList<Pair<ArrayReference,ArrayValue>>();
			changedFields = new HashSet<FieldLVal>();
			readArrays = new HashSet<FieldLVal>();
			backedUpArrays = new HashSet<ArrayReference>();
		}
	}

	public void checkArguments(IJavaValue[] argValues) {
		if (!enabled)
			return;
		for (IJavaValue argValue: argValues) {
			if (argValue instanceof IJavaArray) {
				ArrayReference arr = (ArrayReference)((JDIObjectValue)argValue).getUnderlyingObject();
				if (backedUpArrays.add(arr)) {
					//System.out.println("Backing up arg arr " + (arr == null ? "null" : getValues(arr)));
					argArrs.add(new Pair<ArrayReference, ArrayValue>(arr, ArrayValue.makeArrayValue(arr)));
					disableCollection(arr);
				}
			}
		}
	}
	
	public Set<Effect> getSideEffects() {
		if (!enabled)
			return Collections.emptySet();
		Set<Effect> effects = getEffects();
		if (effects.isEmpty())
			return Collections.emptySet();
		else
			return effects;
	}
	
	public Set<Effect> stopHandlingSideEffects() {
		Set<Effect> effects = getSideEffects();
		effectsMap = null;
		readFieldsMap = null;
		argArrs = null;
		changedFields = null;
		readArrays = null;
		backedUpArrays = null;
		undoEffects(effects);
		return effects;
	}
	
	public boolean isHandlingSideEffects() {
		return addedWatchpoints != null;
	}
	
	public void stop(IProgressMonitor monitor) {
		if (!enabled)
			return;
		try {
			IProgressMonitor curMonitor = SubMonitor.convert(monitor, "Side effect handler cleanup", (prepareBreakpoint == null ? 0 : 1) + (addedWatchpoints == null ? 0 : addedWatchpoints.size()) + (reflectionBreakpoints == null ? 0 : reflectionBreakpoints.size()));
			//long startTime = System.currentTimeMillis();
			if (prepareBreakpoint != null) {  // We disable this before addedWatchpoints since it could add new breakpoints to it.
				prepareBreakpoint.delete();
				curMonitor.worked(1);
				prepareBreakpoint = null;
			}
			if (addedWatchpoints != null) {
				DebugPlugin.getDefault().getBreakpointManager().removeBreakpoints(addedWatchpoints.toArray(new IBreakpoint[addedWatchpoints.size()]), true);
				curMonitor.worked(addedWatchpoints.size());
				addedWatchpoints = null;
			}
			if (reflectionBreakpoints != null) {
				DebugPlugin.getDefault().getBreakpointManager().removeBreakpoints(reflectionBreakpoints.toArray(new IBreakpoint[reflectionBreakpoints.size()]), true);
				curMonitor.worked(reflectionBreakpoints.size());
				reflectionBreakpoints = null;
			}
			for (ObjectReference obj: collectionDisableds)
				obj.enableCollection();
			collectionDisableds = null;
			//System.out.println("stop: " + (System.currentTimeMillis() - startTime));
			curMonitor.done();
		} catch (CoreException e) {
			throw new RuntimeException(e);
		}
	}
	
	public void enable(boolean enable) {
		enabled = enable;
	}
	
	public boolean isEnabled() {
		return enabled;
	}
	
	/*public void enableBreakpoints() {
		enableDisableBreakpoints(true);
	}
	
	public void disableBreakpoints() {
		enableDisableBreakpoints(false);
	}
	
	private void enableDisableBreakpoints(boolean enable) {
		try {
			long startTime = System.currentTimeMillis();
			for (IBreakpoint breakpoint: addedWatchpoints)
				breakpoint.setEnabled(enable);
			for (IBreakpoint breakpoint: reflectionBreakpoints)
				breakpoint.setEnabled(enable);
			System.out.println((enable ? "enable" : "disable") + ": " + (System.currentTimeMillis() - startTime));
		} catch (CoreException e) {
			throw new RuntimeException(e);
		}
	}*/

	public static void redoEffects(Set<Effect> effects) {
		try {
			for (Effect effect: effects) {
				//System.out.println("Redoing " + effect);
				effect.redo();
			}
		} catch (InvalidTypeException e) {
			throw new RuntimeException(e);
		} catch (ClassNotLoadedException e) {
			throw new RuntimeException(e);
		}
	}

	public void redoAndRecordEffects(Set<Effect> effects) {
		for (Effect effect: effects) {
			if (effect.getLval() instanceof FieldLVal) {
				FieldLVal lval = (FieldLVal)effect.getLval();
				if (!(effect.getOldVal().getValue() instanceof ArrayReference))
					recordEffect(lval, effect.getOldVal().getValue(), effect.getNewVal().getValue());
				else
					backupArray(lval, effect.getOldVal().getValue());
			}
		}
		redoEffects(effects);
	}
	
	private class ReflectionBreakpoint extends JavaMethodEntryBreakpoint {
		
		public ReflectionBreakpoint(IMethod method) throws JavaModelException, CoreException {
			super(BreakpointUtils.getBreakpointResource(method.getDeclaringType()), method.getDeclaringType().getFullyQualifiedName(), method.getElementName(), method.getSignature(), -1, -1, -1, 0, true, new HashMap<String, Object>(10));
		}

		@Override
		public boolean handleEvent(Event event, JDIDebugTarget target, boolean suspendVote, EventSet eventSet) {
			try {
				ThreadReference thread = ((LocatableEvent)event).thread();
				StackFrame stack = thread.frame(0);
				ObjectReference fieldValue = stack.thisObject();
				ReferenceType fieldType = fieldValue.referenceType();
				//String className = ((ObjectReference)fieldValue.getValue(fieldType.fieldByName("clazz"))).invokeMethod(thread, event.virtualMachine().classesByName("java.lang.Class").get(0).methodsByName("getName").get(0), new ArrayList<Value>(0), 0).toString();
				String className = ((StringReference)((ObjectReference)fieldValue.getValue(fieldType.fieldByName("clazz"))).getValue(event.virtualMachine().classesByName("java.lang.Class").get(0).fieldByName("name"))).value();
				String fieldName = ((StringReference)fieldValue.getValue(fieldType.fieldByName("name"))).value();
				Field field = event.virtualMachine().classesByName(className).get(0).fieldByName(fieldName);
				List<Value> argValues = stack.getArgumentValues();
				ObjectReference obj = (ObjectReference)argValues.get(0);
				if (!field.isStatic() && obj == null)
					return true;  // The execution will crash.
				Value oldValue = field.isStatic() ? fieldType.getValue(field) : obj.getValue(field);
				if (argValues.size() == 2) {  // We're setting the value of a field.
					Value newValue = argValues.get(1);
					if (newValue instanceof ObjectReference && EclipseUtils.isPrimitive(field.signature()))  // Unbox primitive values.
						newValue = ((ObjectReference)newValue).getValue(((ReferenceType)newValue.type()).fieldByName("value"));
					recordEffect(FieldLVal.makeFieldLVal(obj, field), oldValue, newValue);
				} else if (oldValue instanceof ArrayReference)  // We're reading the value of an array.
					backupArray(FieldLVal.makeFieldLVal(obj, field), oldValue);
			} catch (IncompatibleThreadStateException e) {
				throw new RuntimeException(e);
			}
			return true;
		}
		
	}
	
	private List<ObjectReference> collectionDisableds;
	
	private static void enableCollection(Value value) {
		if (value instanceof ObjectReference) {
			ObjectReference obj = ((ObjectReference)value);
			obj.enableCollection();
		}
	}
	
	private static void disableCollection(Value value) {
		if (value instanceof ObjectReference) {
			ObjectReference obj = ((ObjectReference)value);
			obj.disableCollection();
		}
	}
	
	private void storeCollectionDisableds(Value value) {
		if (value instanceof ObjectReference) {
			ObjectReference obj = ((ObjectReference)value);
			collectionDisableds.add(obj);
		}
	}

}
