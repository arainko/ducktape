### Contributor's guide

`ducktape` follows a standard [fork and pull](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/proposing-changes-to-your-work-with-pull-requests/about-pull-requests) model for contributions via GitHub pull requests.

### Prerequisites

`ducktape` is cross-built for Scala JVM, Scala.js and Scala Native which means you should have [sbt](https://www.scala-sbt.org/), [Node.js](https://nodejs.org/en) and [LLVM/Clang](https://scala-native.org/en/stable/user/setup.html).

### Project structure

`ducktape` is split between 3 platform-specific subprojects `ducktapeJVM`, `ducktapeJS` and `ducktapeNative` all of which share the same codebase (ei. there's no platform-specific source files).

The easiest way to actually develop is to pick the JVM subproject in `sbt` with `project/ducktapeJVM`, you won't have to recompile all of the other stuff on each change which should save you some time.

### Building the project

Run `sbt` and then use any of the following commands:

* `compile`: compiles the code for all platforms
* `test`: runs the tests for all platforms
* `docs/mdoc`: compiles and recreates the `README.md` file
* `scalafmtAll`: formats the code
* `scalafmtSbt`: formats the build definition
* `scalafixAll`: runs all scalafix rules

### Updating the README.md
The README.md file in the root of the repository is generated using `docs/mdoc` - to actually change this file you need to modify `docs/readme.md` and rerun `docs/mdoc` - after this task is done running you should be able to navigate `documentation/target/mdoc/readme.md` to access the modified file and copy it over to `README.md` in the root of the project. This process is pretty manual and cumbersome but I just can't be bothered to automate it.

If you want to iterate on the process you can use `docs/mdoc --watch` to start a server that will automatically reload the docs after a change to a file.
