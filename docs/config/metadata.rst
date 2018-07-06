Metadata
========

Workflows need metadata about the processed data to be able to process the data correctly. Currently, there are two ways how metadata can be
communicated to Roddy workflows: (1) via the filesystem paths of files, or (2) via a metadata table.

Note that this is provisional information as the plan is to streamline the respective code and define a clean interface to these and probably other
metadata sources, such as XMLs, filesystem attributes, dedicated metadata files or databases.

Filesystem-based Metadata
-------------------------

The filesystem-based approach uses of the filename-patterns with specific variables matched against existing filesystem objects.

* outputBaseDirectory: The top-level directory beneath which filename-patterns for input and output files are matched or generated, respectively.
* inputBaseDirectory: Often the same as the outputBaseDirectory, but Roddy can also have a separate input directory in which filename patterns are matched
* outputAnalysisBaseDirectory: defaults to outputBaseDirectory/datasetId
* inputAnalysisBaseDirectory: defaults to inputBaseDirectory/datasetId

The following metadata variables are matched or filled into the filename patterns:

* dataSet
* pid (= patient id, synonymous for dataSet)
* projectName
* USERNAME: The user's username on the submission host (on which the qsub, etc. are executed).
* USERGROUP: The user's primary group on the submission host.
* USERHOME: The user's home directory on the submission host.
* DIR_BUNDLED_FILES, DIR_RODDY: Only valid at the beginning of the path. The absolute path to Roddy's application directory.
* PWD: The execution directory.

Note that Roddy matches variables by the pattern '${' + varname + '}'. Variables that contain references to other variables are written into the job
parameter file, which is later sourced by the Bash-based wrapper that sets up the environment for the actual tool script. At this stage, variables
that are not enclosed by braces, and therefore not considered by Roddy during the ordering of variable assignments in the parameter file, may or may
not be bound. So better use braces!

Additional variables can be defined in plugins. Furthermore, all configuration values can be referenced.

Table-based Metadata
--------------------

MDTs differentiate between internal column names that are used in the Java/Groovy code to select columns from external column names that are used in
the input-files. External column names can be chosen freely using alpha-numeric characters.

The basic version of the metadata table (MDT) needs two columns to match files against datasets. Therefore the minimal requirement for a metadata
table is to have a dataset column with internal identifier "datasetCol" and a file column with internal identifier "fileCol".

The mapping between internal and external columns is defined in the XML as configuration values. Each internal column name, like "datasetCol", needs
to be defined as configuration value with the value itself representing the external column name. With this mapping actual columns in the input files
are mapped to the correct semantics internally and thus the order of columns in MDT inputs can be arbitrary. Please make heavy use of "description"
attributes for your configuration values to ensure the exact semantics of the MDT columns is communicated well.

Furthermore there probably should be a configuration value "metadataTableColumnIDs" that defines a priority for internal column identifiers -- with high
priority first and lower priority later. The priority allows simple checks on the content of the MDT. Given a set of rows, all higher priority fields
need to have identical values. This check is optional and depends on the which API the workflow developer has used in its Java code.
