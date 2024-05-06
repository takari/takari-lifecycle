# Introducing Takari and TEAM

This book documents the plugins and features available as the Takari Extensions for Apache Maven (TEAM) as well
associated components and tools, that improve your usage of Maven. This introduction defines what the TEAM distribution
is and introduces the company behind this new distribution.

## What is TEAM?

TEAM stands for the Takari Extensions for Apache Maven. TEAM is a collection of
supported Maven plugins and extensions to a core Apache Maven. It is made freely
available by Takari. Takari is creating new releases every 30-60 days depending
on the current development schedule.

TEAM was created to address several shortcomings in the "stock" Maven
distribution and TEAM includes the following features beyond that of Maven:

1. Support for incremental build operations

1. An intelligent approach to the parallelization of Maven builds

1. An alternative to SNAPSHOT releases called Generations

1. Improved testing features and support

**TEAM: Advanced Use Cases for Maven**

While TEAM's features are relevant to all Maven users, these features and
plugins were designed to support development at scale - on projects with
hundreds or thousands of developers. These advanced builds are often
characterized by large networks of interdependent groups building and delivering
a steady stream of software to production dealing with challenges that arise
when an organization has a a large number of components with often conflicting
release schedules.

On such large projects the key to success is agility, the pace with which new
features can be implemented and additional releases can be delivered. These
projects can rarely stop and wait for a formal release process that takes hours
to complete. Individual developers are most productive when they can focus on
incremental builds that don't cause them to set aside hours or days for
integration.

TEAM can be used by any Maven user, but TEAM was specifically designed for the
needs of large software projects. The features added to TEAM cater to issues that
arise when hundreds or thousands of developers are collaborating on fast-moving
projects.

## What is Takari?

Takari is a company founded by Jason van Zyl focused on creating software to
manage component-based development and to support builds at scale. Takari's
developers bring multiple decades of experience building software systems to our
customers. We know about creating and documenting large open source projects
including Maven among others from years of actually running them.

**Sustainable Open Source Development**

Takari is committed to practicing sustainable open source development and
building a community that understands exactly how open source developers and
community members must be active participants to ensure the ongoing health of an
open source project.

**Integrity and Authenticity**

As open source developers we believe in doing the right thing, in a reliable
way and are committed to being genuine in our actions and reactions. Everyone
from our developers to our executives understands that our actions must be
consistent with our community.

**Our Customers are Our Investors**

Our customers fund our day-to-day operations by paying for our training,
services and products. We answer to our customers so we can continue to focus
our energy where it matters most — on creating high-quality, useful products for
the community.

**Community Support is Key**

Staying involved and continuing to support the projects that are such a large
part of where we came from is important to us. While we are focused on
delivering quality software to our supporters we are also cognizant of the
larger community.

## Evolving Challenges - Builds at Scale

The efforts of Takari related to TEAM are influenced by the following industry
trends:

**Changing Technology - Changing Conventions**

What worked 10 years ago may not be appropriate for today’s builds, but the
core concepts that drove the creation of Maven are still valid today. Convention
over configuration is even more appropriate now than it was then given the
amount of variation introduced by polyglot development. With new languages, new
production architectures, and a growing array of tools, Maven needs more than
just a few new plugins to support new tools. It needs a comprehensive overhaul
to allow for continued adaptation.

Incremental and parallel build improvements allow Maven to be used for a number
of use-cases such as incremental compilation, incremental processing of
Javascript resources, and other requirements which may not have been of primary
concern in 2004.

TEAM updates the concept of SNAPSHOTS for complex projects and replaces it with
Generations. This is an approach to tracking software releases and relating
specific point-in-time releases to a commit or branch in a distributed version
control system.

Over time TEAM will release updates to the core APIs and models of Maven to
allow for easier integration with different languages, tools, and technology. It
is Takari's goal to make sure that TEAM's regular releases can fill in the gaps
between Maven's far less frequent releases so that changes in technology can be
quickly addressed by TEAM.

**Faster Lifecycles: More Frequent Releases**

When Maven was created we were aiming at projects that needed to conduct a
weekly or monthly software release for a relatively well-defined project. When
Maven was still new, the industry didn't have projects beyond a certain level of
complexity because the easy, component-based approach to development in Java
hadn't yet been enabled by Maven. Projects were more limited in scope then they
are today. In addition to differences in scope, projects weren't nearly as
complex and interdependent as projects Takari supports in the field in 2014.

Today, we see large organizations with hundreds or thousands of developers.
These organizations are building very complex, interdependent systems which
depend upon Maven to facilitate both continuous integration and software
releases. Where a company may only push to production once a month in 2004 or
even less frequently, that same company expects to be able to push to production
as often as possible even multiple times a day. This is the emerging reality of
enterprise software development and Maven's legacy approach to Releases and
SNAPSHOTs does not lend itself to these, more iterative and agile workflows.

TEAM's generations features as well as incremental and parallel builds are aimed
squarely at created more timely and efficient builds for organizations that are
looking to push to production frequently.

## Installing TEAM

This chapter covers the installation process for the Takari Extensions for Apache Maven - TEAM.

Before you start installing TEAM there are a few things to establish. The
following sections outline a few assumptions about the audience for this chapter
as well as the prerequisites necessary for a successful installation.

**Assumptions**

One of the assumptions of TEAM is that you are already somewhat familiar with
Maven terminology. You understand how to install Maven, and you also understand
how to run Maven from the command-line. The good news is that, if you know how
to do these two things, the installation process should be very easy for you.

If you are unfamiliar with Maven terminology, and if you have never installed
Maven before, we suggest that you refer to the existing documentation or attend
a Takari Maven training. In general, a familiarity with Maven will make the
installation and setup process of TEAM very easy to understand.

**Prerequisites**

TEAM is designed and tested for

* Microsoft Windows 7 or higher
* Apple OSX 10.7 or higher and
* Modern Linux Distributions

with the **Oracle Java Development Kit JDK version 7** installed. You can verify
your JDK installation by running `java -version` which should result in an
 output similar to

```
$java -version
java version "1.7.0_65"
Java(TM) SE Runtime Environment (build 1.7.0_65-b17)
Java HotSpot(TM) 64-Bit Server VM (build 24.65-b04, mixed mode)
```

Depending on your particular system and setup procedures, you may need
administrative access to the machine you are installing TEAM on. If you following
the instructions outlined below, you will certainly need administrative access,
but if you understand what you are doing you may be able to get away with
running TEAM from a directory in your home directory. We leave this customization
to the reader.

**Downloading**

TEAM can be downloaded from the Central Repository at
`https://repo.maven.apache.org/maven/io/takari/takari-team-maven/`. This location
contains all released versions. The TEAM distribution is available as both a
GZip'd tar archive in each version specific folder following the Maven
repository format's naming convention for the archive. E.g. you can download
version 0.9.0 of TEAM from

```
https://repo1.maven.apache.org/maven2/io/takari/takari-team-maven/0.9.0/takari-team-maven-0.9.0.tar.gz
```

resulting in a downloaded archive file name of `takari-team-maven-0.9.0.tar.gz`.

**Installing**

There are two ways to install TEAM on your computer. You can download a complete
distribution of TEAM which includes Apache Maven. Alternatively you can run an
installer that will turn a compatible installation of Apache Maven 3 into a
functioning installation of TEAM. The second option was created for environment
in which Maven is already installed to make it easier to migrate large groups of
developers to the supported TEAM distribution.

**Installing a TEAM Distribution**

Installing the TEAM distribution is easy, and if you are familiar with
installing Maven you'll notice the similarities. Once you have downloaded the
archive extract it with a command line tool like 'tar' or one of the
many available archive management applications for your operating system.

```
tar xvzf takari-team-maven-1.0.0.tar.gz
```

Successful extraction will create a directory with the same name as the archive
file, omitting the extension.

```
takari-team-maven-1.0.0
```

As a next step you need to move this directory to a suitable location. The
only requirements is that the user that will run TEAM has read access to the
path.

We suggest to follow the operating system specific recommendations e.g. on
Linux or OSX install TEAM into `/opt` or `/usr/local` and avoid path names containing
spaces such as `Program Files`.

```
/opt/takari-team-maven-1.0.0
C:\tools\takari-team-maven-1.0.0
```

The next steps should be just as familiar from a standard Maven installation as
the simple archive extraction - create a `M2_HOME` environment variable that
points to the folder you just created and add `M2_HOME/bin` to the `PATH`.

On Linux or OSX you can configure this e.g., in your `~/.profile` file with

```
export M2_HOME=/opt/takari-team-maven-1.0.0
export PATH=M2_HOME/bin:$PATH
```

On Windows you typically configure this via the user interface as a system
environment variable. On the command line you can use the set command:

```
set M2_HOME=c:\tools\takari-team-maven-1.0.0
```

Note that the usage of the environment variable is done
via `%M2_HOME%` as compared to `$M2_HOME`, that the delimiter in the path
definition is a semicolon and the path separator is a backslash so your PATH
modification will look similar to

```
%M2_HOME%\bin;%PATH%
```

**Upgrading an Existing Apache Maven Installation**

To upgrade an existing Apache Maven installation....

```
mvn team:install or whatever
```

**Verifying your TEAM Installation**

Once you have installed the TEAM distribution, you should verify your setup
by running `mvn -v` or `mvn --version`, which should display the TEAM version:

```
$ mvn -v
Takari Extensions for Apache Maven (TEAM) 0.9.1-SNAPSHOT
(72d4cce; 2014-10-14T11:12:43-07:00)

Including:
 --> Apache Maven: 3.2.4-SNAPSHOT
 --> Smart Builder: 0.3.0
 --> Concurrent Safe Local Repository: 0.10.4
 --> OkHttp Aether Connector: 0.13.1
 --> Logback with Colour Support: 1.0.7
 --> Incremental Build Support: 0.9.0+

http://takari.io/team

Maven home: /opt/tools/takari-team-maven-0.9.1-SNAPSHOT
Java version: 1.7.0_65, vendor: Oracle Corporation
Java home: /Library/Java/JavaVirtualMachines/jdk1.7.0_65.jdk/Contents/Home/jre
Default locale: en_US, platform encoding: UTF-8
OS name: "mac os x", version: "10.8.5", arch: "x86_64", family: "mac"
```

The same output will be created with the `-V` or `--show-version` parameters. It
details the version of TEAM as well as the components of it e.g. Apache Maven,
Smart Builder and others.

## Eclipse Support for TEAM

Any TEAM plugins and components needed for development with Eclipse and M2e are
setup to be automatically installed. Alternatively you can manually install the
components.

[//]: # (TBD need to add some URLs or whatever else here, maybe screenshots or whatever)





