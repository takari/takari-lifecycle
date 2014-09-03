Compared to maven-verifier
* supports m2e workspace and reactor build 'chaining', that is, artifacts available 
  in outer workspace or reactor are available to the test builds.
* in-process maven launcher (Embedded3xLauncher) supports multiple maven versions
* (subjective) I like the API better


Hudson users beware. Hudson Maven 3 does not use -s/--settings standard maven command
option to enable "managed" settings.xml files in maven builds. Because of this, test 
JVMs launched by Maven builds are not able to use the settings.xml files will 
fail with artifact resolution errors. To workaround, use filesystem-based settings xml
files and pass them using -s/--settings options.
