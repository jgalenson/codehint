# CodeHint

CodeHint is a tool that synthesizes code from user-provided partial dynamic specifications of the desired behavior.  [Here](http://www.cs.berkeley.edu/~joel/codehint/demo.html) is a demo.

If you find any bugs, would like to request any features, or have any general comments, please email joel at cs dot berkeley dot edu.

*Warning*: CodeHint actually executes some expressions, which could contain external side effects. We use Java's security manager to stop effects like deleting files, but it is possible that there will be undesirable external side effects inside native calls. Users may disallow native calls to methods outside the standard library when using CodeHint and so should be careful when the current context contains objects with such external side effects in native calls.

### Requirements:
- Java (tested with Oracle's VM versions 6 and 7, though 5 should work).
- A new version of Eclipse (3.7/Indigo, 4.2/Juno, or 4.3/Kepler) with the Java plugins.

### Installing the plugin:
1. Open Eclipse and choose the "Help -> Install New Software" menu item.
2. In the "Work with" box at the top, enter "http://www.cs.berkeley.edu/~joel/codehint/eclipse/".  Click "Add", name the site, and press "OK".
3. Select the plugin in the list below, install it by clicking "Next" a few times, and restart Eclipse when prompted.
4. When the plugin loads for the first time, it will open the preferences page to ask if you want to allow it to report anonymous usage information. We would appreciate it if you allow it to do so. You may change this setting at any time by returning to the preferences page.
    - The information collected contains data about how you use CodeHint and how it performs.  This information includes the specifications you give but none of your code.

### Brief user guide:
A more detailed tutorial is available [here](http://www.cs.berkeley.edu/~joel/codehint/tutorial/tutorial.html).

1. Open Eclipse to a Java project and navigate to where you want to add code.
2. Set a breakpoint where you want to add code, start the debugger, and navigate to that breakpoint.
3. If you want to change the value of a variable, right-click the variable whose value you want to change in the Variables View window in the top-right of the Debug perspective. You may also click the "Synthesize" button (labelled "CH free") in the toolbar. You can now give one of the following types of specifications:
    - Demonstrate type: Enter the name of a type to find expressions that evaluate to values of that type. This can be useful when trying to use an unfamiliar API.
    - Demonstrate value: Enter an expression to find expressions that evaluate to the same value. This is often useful when searching for integers and strings or as a small unit test.
    - Demonstrate property: Enter an expression that refers to the pre- and post-states of variables (where the latter are primed). This allows you to enter an arbitrary Java expression to use as a specification. Some examples are:
        - `x' > x` will find expressions that increase `x`.
        - `x'.contains("--arg")` can be used to find lists that contain a certain element, for example.
        - `x'.toString().contains("Hello")` will find expressions whose `toString` contains `"Hello"`. This can be useful when you have only a vague idea about what you want. 
4. Optional Enter a skeleton of what the missing code should look like. A `??` stands for missing expressions or names and a `**` stands for an unknown number of arguments. Examples include:
- `??`, which is the default, will search for an arbitrary expression.
- `foo.??` will search for a field access on the `foo` object.
- `foo.??(x)` will search for calls to one-argument methods of the `foo` object with `x` as the argument.
- `??.??().??` will search for field accesses of zero-argument method calls of arbitrary expressions.
- `foo.??(**)` will search for calls to methods of the `foo` object with any number of unknown expressions. 
5. If you want to search calls to constructors of the desired type, check the "Search constructors" button. If you want to search operators such as `+` and `<`, check the "Search operators" button. If you want to avoid making calls to native methods to avoid any external side effects, uncheck the "Call native methods" button, which will block such calls at the cost of slowing down the search. If you want to see all the side effects of the expressions and undo them so they do not affect subsequent evaluations, check the "Log and undo side effects" button, which will slow down the search.
6. Click the "Search" button. You will be shown expressions that satisfy your specifications and their values. Select the ones you wish to insert into the code and click "OK". If you do not see an expression you want, you may modify your specification and/or skeleton and try again. You may also click "Continue Search" to search more expressions with the same specification, but be aware that this search may take a long time and need to be cancelled.  You may type words in the filter box at the bottom of the dialog and press "Filter" to keep only expressions whose text, result, or Javadocs contain the given words.
7. The desired expressions will be inserted into the code. You can look through them and select the correct one (by manually editing the code) if you desire. Alternatively, you can bring the debugger back to this line (perhaps by continuing the execution or giving another testcase) and give another specification in this new state to prune the set of candidate expressions.
- Optional The added code contains calls to a small runtime library. If you want this code to be executable, you may download the library from [here](http://www.cs.berkeley.edu/~joel/codehint/codehint-lib.jar) and add it to the classpath of the current project.  This will additionally provide some performance improvements.
