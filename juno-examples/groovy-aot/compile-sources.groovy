// Compiles every .groovy file under a source root into an output directory with groovyc.
// Run by exec-maven-plugin (see pom.xml): args are <source-root> <output-directory>.
import groovy.io.FileType
import org.codehaus.groovy.tools.FileSystemCompiler

def sourceRoot = new File(args[0])
def outputDirectory = args[1]
def sources = []
sourceRoot.eachFileRecurse(FileType.FILES) { file ->
    if (file.name.endsWith('.groovy')) {
        sources << file.path
    }
}
if (!sources.isEmpty()) {
    FileSystemCompiler.commandLineCompile((['-d', outputDirectory] + sources) as String[])
}
