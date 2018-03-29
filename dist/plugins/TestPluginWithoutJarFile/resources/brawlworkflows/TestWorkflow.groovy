/**
 * Test workflow for the new Brawl Workflow DSL
 */
// Configuration
String variable = "abc"

cvalue "valueString", "a text", "string"
cvalue "valueInteger", 1
cvalue "valueDouble", 1.0
cvalue "aBooleanValue", true
cvalue "runEverything", true

// Explicit workflow. Implicit might follow later
explicit {
    def file = getSourceFile("/tmp", "TextFile")
    def flag = getflag "runEverything"
    println flag
    def a = run "ToolA", file
}

// Tool / Rule section
rule "ToolA", {
    input "TextFile", "parameterA"
    output "aClass", "parameterB", "/tmp/someoutputfile"
    shell """
                #!/bin/bash
                echo "\$parameterA"
                echo "\$parameterB"
                touch \$parameterB

            """
}
