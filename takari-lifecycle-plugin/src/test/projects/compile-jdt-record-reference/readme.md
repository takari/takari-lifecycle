== (my naive understanding) of possible type references
imports
on-demand imports <<< dependency on a package, it is an error to import packages that do not exist

static import
type parameter extends/superclass
extends
implements
method parameter type extends/superclass
method return value
method parameter types
method throws
local variable types
static method invocation
new constructor invocation
type cast
throw exception
catch exception, including multi-type catch


local type extends, implements
anonymous type superclass/superinterface
nested types (everything above)


package-info.java in this package, can make the class deprecated, for example


== open questions

=== missing on-demain import
compiler currently detects and ignores missing on-demand imports. For example,

   import missing.*;
   class C {
      Name name;
   }

Ideally, class 'C' needs to be recompiled whenever package 'missing' becomes
available. This requires ability to track dependency on packages

But at very least need to track dependency on missing.Name

== package-info.java
can package-info.java in other packages result in more/less compilation errors and warnings?


== random notes

=== interesting discussion about shadowing and obscuring
https://bugs.eclipse.org/bugs/show_bug.cgi?id=318401#c1
