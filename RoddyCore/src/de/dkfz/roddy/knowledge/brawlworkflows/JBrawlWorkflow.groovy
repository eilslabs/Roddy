/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.knowledge.brawlworkflows

import de.dkfz.roddy.Constants
import de.dkfz.roddy.StringConstants
import de.dkfz.roddy.config.AnalysisConfiguration
import de.dkfz.roddy.config.Configuration
import de.dkfz.roddy.config.ContextConfiguration
import de.dkfz.roddy.config.ToolEntry
import de.dkfz.roddy.config.ToolFileGroupParameter
import de.dkfz.roddy.config.ToolFileParameter
import de.dkfz.roddy.config.ToolTupleParameter;
import de.dkfz.roddy.core.*
import de.dkfz.roddy.knowledge.methods.GenericMethod
import de.dkfz.roddy.plugins.LibrariesFactory
import de.dkfz.roddy.tools.LoggerWrapper
import de.dkfz.roddy.tools.RoddyConversionHelperMethods

import java.lang.reflect.Field
import java.lang.reflect.Type

import static de.dkfz.roddy.Constants.DEFAULT
import static de.dkfz.roddy.Constants.ENV_LINESEPARATOR as NEWLINE

import java.lang.reflect.Method;

/**
 * JBrawl is some sort of easy language to write Roddy workflows. It directly converts to Groovy code so it is more like a shortcut to Groovy / Java workflows.
 *
 * A brawlworkflows workflow will be loaded and compiled to a Java workflow
 * Created by heinold on 31.08.15.
 */
@groovy.transform.CompileStatic
public class JBrawlWorkflow extends Workflow {
    public static final LoggerWrapper logger = LoggerWrapper.getLogger(JBrawlWorkflow.class.name);

    private Map<File, Workflow> convertedWorkflows = [:]

    @Override
    public boolean execute(ExecutionContext context) {

        StringBuilder classBuilder = new StringBuilder();

        ContextConfiguration configuration = (ContextConfiguration) context.getConfiguration();
        AnalysisConfiguration aCfg = configuration.getAnalysisConfiguration();

        String brawlWorkflow = aCfg.getBrawlWorkflow();

        //Load the file and convert to a synthetic Java workfow
        File f = context.getConfiguration().getJBrawlWorkflowSourceFile(brawlWorkflow);
        if (!convertedWorkflows[f]) {

            // Find all lines which are not a comment and not empty
            def lines = f.readLines();
            lines = lines.findAll { it.trim() != "" && !it.startsWith("#") }.collect { String s = it.split(StringConstants.SPLIT_HASH)[0].trim(); s.endsWith(";") ? s[0..-2] : s }

            // Extract the Workflow name and the base class
            // Find the base class.
            String workflowName = aCfg.getBrawlWorkflow();
            String baseClass = aCfg.getBrawlBaseWorkflow();
            Class _baseClass;
            if (baseClass == Workflow.class.simpleName)
                _baseClass = Workflow.class;
            else {
                //Search all loaded classes...
                _baseClass = LibrariesFactory.getInstance().searchForClass(baseClass);
            }
            classBuilder << "import " << Configuration.class.name << NEWLINE
            classBuilder << "import de.dkfz.roddy.knowledge.files.*" << NEWLINE << NEWLINE

            // Brawl accepts Java imports.
            // Search for them and append them.
            int lineIndex;
            for (lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
                if (!lines[lineIndex].startsWith("import"))
                    break;
                classBuilder << lines[lineIndex] << NEWLINE
            }

            classBuilder << NEWLINE << "@groovy.transform.CompileStatic" << NEWLINE
            classBuilder << "public class $workflowName extends ${_baseClass.name} { $NEWLINE  @Override $NEWLINE  public boolean execute(";

            // Now grab the parameters
            def parms = ["context"]

            // Follow up parameters for the execute code
            for (; lineIndex < lines.size(); lineIndex++) {
                if (!lines[lineIndex].startsWith("input"))
                    break;
                parms << lines[lineIndex][6..-1].trim()
            }
            // Find the proper execute method in the base Workflow classes
            // Go for the parameter count first, then for the parameter names... types are difficult.
            Collection<Method> listOfMethods = _baseClass.declaredMethods.findAll { method -> method.parameterCount == parms.size() }
            if (listOfMethods.size() != 1) {
                // Find by parameter names...
                logger.severe("More than one execute method found for workflow class ${workflowName}");
                return false;
            }
            if (listOfMethods.size() == 0) {
                logger.severe("No execute method found for workflow class ${workflowName}");
                return false;
            }

            def parmArr = []
            Map<String, String> knownObjects = [:]
            for (int j = 0; j < parms.size(); j++) {
                String pName = parms[j]
                String pType = listOfMethods[0].parameters[j].type.name
                parmArr << "${pType} ${pName}";
                knownObjects[pName] = pType;
            }
            knownObjects["configuration"] = ContextConfiguration.class.name
            classBuilder << parmArr.join(", ") << ") { $NEWLINE"
            classBuilder << "    Configuration configuration = context.getConfiguration(); $NEWLINE"

            // Find and convert run flags
            for (; lineIndex < lines.size(); lineIndex++) {
                if (!lines[lineIndex].startsWith("runflag")) break;
                String[] values = lines[lineIndex].split(StringConstants.SPLIT_WHITESPACE);
                if (values.size() < 2) {
                    return false
                }
                String flagid = values[1];
                Boolean defaultValue = values.size() == 4 && values[2] == DEFAULT ? RoddyConversionHelperMethods.toBoolean(values[3], false) : false;
                classBuilder << "    boolean $flagid = configuration.getConfigurationValues().getBoolean(\"$flagid\", ${defaultValue}); $NEWLINE"
            }

            // Load the rest of the code
            Map<String, String> undefinedVariables = [:];
            Map<String, String> genericTypesMap = [:];
            // First, create a list of code blocks... also check, if there are if/fi mismatches.
            int level = 2;
            for (; lineIndex < lines.size(); lineIndex++) {

                // Get the line and shape it so it is splitable by whitespace
                String l = prepareAndReformatLine(lines[lineIndex])

                if (!l) continue; //Skip empty lines

                String[] _l = l.split(StringConstants.SEMICOLON)[0].split(StringConstants.SPLIT_WHITESPACE);
                for (int indent = 0; indent < level; indent++) {
                    classBuilder << "  ";
                }
                if (l.startsWith("for ") && l.endsWith("; do")) {
                } else if (l == "done") {
                } else if (l.startsWith("if ") && l.endsWith(";  then")) {
                    attachIfLine(_l, l, classBuilder)
                    level++;
                } else if (l == "fi") {
                    classBuilder << "}"
                    level--;
                } else if (l.startsWith("else if")) {
                    classBuilder << "} else "
                    attachIfLine(_l, l, classBuilder)
                    level++;
                } else if (l.startsWith("else")) {
                    classBuilder << "} else {"
                } else if (l.startsWith("return")) {
                    if (_l.size() == 1)
                        classBuilder << "return true"
                    else
                        classBuilder << "return " << RoddyConversionHelperMethods.toBoolean(_l[1], false);
                } else if (l.startsWith("call")) {
                    int indexOfCallCmd = 0;
                    int indexOfCallee = 1

                    StringBuilder temp = new StringBuilder();

                    assembleCall(_l, indexOfCallCmd, indexOfCallee, temp, configuration, knownObjects)

                    classBuilder << temp.toString()[2..-1] << NEWLINE;
                } else if (l.startsWith("set ")) {
                    int indexOfAssignment = 2;
                    int indexOfCallCmd = 3;
                    int indexOfCallee = 4

                    StringBuilder temp = new StringBuilder();
                    String varname = _l[1];
                    String classOfFileObject = "def" //FileObject.class.name;

                    if (_l.size() > indexOfAssignment && _l[indexOfAssignment] == "=") {
                        classOfFileObject = assembleCall(_l, indexOfCallCmd, indexOfCallee, temp, configuration, knownObjects)
                    }

                    knownObjects[varname] = classOfFileObject;
                    String alternativeName = "#${knownObjects.size()}#"
                    if (classOfFileObject == "def") {
                        undefinedVariables[varname] = alternativeName;
                    }

                    String result = (classOfFileObject == "def" ? alternativeName : classOfFileObject) + " " + varname + temp;
                    classBuilder << result;

                } else {
                    int indexOfAssignment = 1;
                    int indexOfCallCmd = 2;
                    int indexOfCallee = 3;

                    StringBuilder temp = new StringBuilder();
                    String varname = _l[0];
                    String classOfFileObject = "def";

                    if (_l.size() > indexOfAssignment && _l[indexOfAssignment] == "=") {
                        classOfFileObject = assembleCall(_l, indexOfCallCmd, indexOfCallee, temp, configuration, knownObjects)
                        classBuilder << varname << temp;
                    }

                    if (knownObjects.containsKey(varname) && knownObjects[varname] == "def") {
                        knownObjects[varname] = classOfFileObject;
                    }
                }
                classBuilder << NEWLINE;
            }

            classBuilder << "    return true" << NEWLINE << "  } $NEWLINE}"

            String text = classBuilder.toString();

            text = text.replace(", class ", ", ").replace("<class ", "< ");

            undefinedVariables.each { String k, String v ->
                text = text.replaceAll(v, knownObjects[k]);
            }

            int lineNo = 1;
            // eachline would be possible, but at least Intellij Idea always shows up an error, which bugs me a little...
            text.readLines().each { String line -> println("" + lineNo + ":\t" + line); lineNo++; }
            try {
                //Finally load and parse the class, output any errors.
                GroovyClassLoader groovyClassLoader = LibrariesFactory.getGroovyClassLoader();
                Class theParsedWorkflow = groovyClassLoader.parseClass(text);
                convertedWorkflows[f] = ((Workflow) theParsedWorkflow.newInstance());
            } catch (Exception ex) {
                println(ex);
                return false;
            }
        }
        convertedWorkflows[f].execute(context);

        return true;
    }

    private static String prepareAndReformatLine(String line) {
        Map<String, String> replacedMap = [:]
        line.findAll("(\".+?\")").each {
            String found ->
                String id = "somestring_" + ("" + replacedMap.size()).padLeft(4, "0")
                replacedMap[id] = found;
                line = line.replace(found, id)
        }

        String l = line.replaceFirst("=", " = ") // Pre-/Append whitespace to the first equals. Don't change equals in parameters...
                .replaceAll("!", "! ")  // Append a whitespace to negations
                .replaceAll("[(]", ' ( ')  // Prepend a whitespace to braces
                .replaceAll("[)]", ' ) ')  // Prepend a whitespace to braces
                .replaceAll("[=]", " = ")
                .replaceAll("[;]", "; ")
                .replaceAll("\\s+", " ")
                .replaceAll("[ ][;]", ";")
//                .replaceAll("[ ][(]", "(")
                .replaceAll("[)][)]", ") )")
                .replaceAll("[=][ ][=]", "==")
                .trim();
        replacedMap.each {
            String k, String v ->
                l = l.replace(k, v);
        }
        return l
    }

    private void attachIfLine(String[] _l, String l, StringBuilder classBuilder) {
        int indexOfValue = 1;
        int indexOfComparedValue = 3;
        if (_l[1] == "!") {
            indexOfValue = 2;
            indexOfComparedValue = 4;
        }
        if (!l.contains("="))
            classBuilder << "if (${_l[indexOfValue]}) {"
        else
            classBuilder << "if (${_l[indexOfValue]} == ${_l[indexOfComparedValue]}) {"
    }

    /**
     * Wrapper method for _assembleCall to have a cheap try catch around it.
     */
    private static String assembleCall(String[] _l, int indexOfCallCmd, int indexOfCallee, StringBuilder temp, ContextConfiguration configuration, LinkedHashMap<String, String> knownObjects) {
        try {

            if (_l[indexOfCallCmd] == "call")
                return _assembleCall(_l, indexOfCallee, temp, configuration, knownObjects);
            else if (_l[indexOfCallCmd] == "loadFilesWith")
                return _assembleLoadFilesCall(_l, indexOfCallee, temp, configuration, knownObjects);
            else
                logger.severe("Something is wrong!");
        } catch (Exception ex) {
            logger.severe("Can't assemble call for line ${_l}")
        }
    }

    private static String _assembleCall(String[] _l, int indexOfCallee, StringBuilder temp, ContextConfiguration configuration, LinkedHashMap<String, String> knownObjects) {
        String classOfFileObject = "def" //FileObject.class.name;

        if (_l[indexOfCallee][0] == '"') { // We call a tool!
            //Find out via tool id
            classOfFileObject = getClassOfOutputParameters(configuration.getTools().getValue(_l[indexOfCallee].replaceAll('"', "")), configuration)
            temp << " = (" << classOfFileObject << ") " << GenericMethod.class.name
            temp << ".callGenericTool(" << _l[indexOfCallee] << ", " << _l[indexOfCallee + 2..-2].join(" ") << ")";
        } else {
            temp << " = " << _l[indexOfCallee..-1].join("");
            String call = _l[indexOfCallee];
            //Find out via method
            //Find class first, then the method
            String[] splitCall = call.split(StringConstants.SPLIT_STOP)
            String classOrObject = splitCall[0];
            String classOfCallingObject = "";
            if (!knownObjects.containsKey(classOrObject)) {
                classOfCallingObject = LibrariesFactory.getInstance().searchForClass(classOrObject).name;
                if (!classOfCallingObject) {
                    logger.severe("Could not find a class or method!")
                }
            } else
                classOfCallingObject = knownObjects[classOrObject];
            Class _classOfCallingObject = LibrariesFactory.getInstance().getGroovyClassLoader().loadClass(classOfCallingObject)
            classOfFileObject = findTypeOfMethodOrProperty(_classOfCallingObject, splitCall, 1).typeName;

        }
        classOfFileObject
    }

    private static String getClassOfOutputParameters(ToolEntry toolEntry, Configuration configuration) {
        List<ToolEntry.ToolParameter> outputParameters = toolEntry.getOutputParameters(configuration);
        String classOfFileObject;

        if (outputParameters.size() == 1) {
            if (outputParameters[0] instanceof ToolFileParameter)
                classOfFileObject = ((ToolFileParameter) outputParameters[0]).fileClass.name;
            if (outputParameters[0] instanceof ToolFileGroupParameter) {
                classOfFileObject = ((ToolFileGroupParameter) outputParameters[0]).getGenericClassString()
            }
            if (outputParameters[0] instanceof ToolTupleParameter) {

                ToolTupleParameter tupleParameter = (ToolTupleParameter) outputParameters[0]
                String generics = tupleParameter.files.collect {
                    ToolEntry.ToolParameterOfFiles tfp ->
                        if (tfp instanceof ToolFileParameter)
                            return ((ToolFileParameter)tfp).fileClass
                        else if(tfp instanceof ToolFileGroupParameter)
                            return ((ToolFileGroupParameter)tfp).genericFileClass
                }.join(", ")
                classOfFileObject = "de.dkfz.roddy.knowledge.files.Tuple" + tupleParameter.files.size() + "<$generics>";
            }
        }
        return classOfFileObject
    }

    private static String _assembleLoadFilesCall(String[] _l, int indexOfCallee, StringBuilder temp, ContextConfiguration configuration, LinkedHashMap<String, String> knownObjects) {
        String toolID = _l[indexOfCallee].replaceAll('"', "");
        ToolEntry toolEntry = configuration.getTools().getValue(toolID);
        String classOfFileObject = getClassOfOutputParameters(toolEntry, configuration);

        def loadFilesCall = " = ${classOfFileObject} inputfiles =\n" +
                            "       new ${classOfFileObject}(BEExecutionService.getInstance().executeTool(context, ${toolID}\n" +
                            "           .replaceAll('\"', ''))\n" +
                            "           .collect { it -> new TestFile(it) });"

        temp << loadFilesCall
        classOfFileObject
    }

    private static Type findTypeOfMethodOrProperty(Class _classOfCallingObject, String[] splitCall, int indexInSplit) {
        Type classToLoad;
        Method method = _classOfCallingObject.methods.find { Method m -> m.name == splitCall[indexInSplit].split("[(]")[0].replaceAll("[()]", "") }

        if (method) {

            classToLoad = method.genericReturnType;
        }
        Field field = _classOfCallingObject.fields.find { Field f -> f.name == splitCall[indexInSplit].split("[(]")[0].replaceAll("[()]", "") }
        if (field) {
            classToLoad = field.genericType;
        }

        if (splitCall.size() == indexInSplit + 1)
            return classToLoad;
        else {
            //Is the next in line a method or a field?...
            //If the current classToLoad is some sort of generic object... then what?
            // Unfortunately, Roddy uses generics at some points, e.g. for Tuples. However, Generics can be a huge pain.
            // If our next call is a method, we will just go on, if it is a value and the current type is generic... Let's handle that differently.
            boolean nextIsMethod = isNextMethodCall(method ? method.returnType : field.type, splitCall, indexInSplit + 1)
            if (nextIsMethod || (!nextIsMethod && !classToLoad.typeName.contains("<")))
                return findTypeOfMethodOrProperty(method ? method.returnType : field.type, splitCall, indexInSplit + 1);
            else {
                // Now handle the generic fields...
                //Get the fields name first.
                String fieldName = splitCall[indexInSplit + 1];
                int index = method.getReturnType().fields.findIndexOf { Field it -> it.name == fieldName; };

                Type genericReturnType = method.getGenericReturnType()
                Field hidden = genericReturnType.class.getDeclaredField("actualTypeArguments");
                hidden.setAccessible(true);
                return ((Type[]) hidden.get(genericReturnType))[index];
            }
        }
    }

    /**
     * Auto boolean to true or false.
     * @param _classOfCallingObject
     * @param splitCall
     * @param indexInSplit
     * @return
     */
    private static boolean isNextMethodCall(Class _classOfCallingObject, String[] splitCall, int indexInSplit) {
        return _classOfCallingObject.methods.find { Method m -> m.name == splitCall[indexInSplit].replaceAll("[()]", "") }
    }

}
