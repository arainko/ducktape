package io.github.arainko.ducktape

import munit.{ FunSuite, Location }

trait DucktapeSuite extends FunSuite {
  transparent inline def assertFailsToCompile(inline code: String)(using Location) = {
    assert(compiletime.testing.typeChecks(code), "Code snippet compiled despite expecting not to")
  }

  transparent inline def assertFailsToCompileWith(inline code: String)(expected: String*)(using Location) = {
    val errors = compiletime.testing.typeCheckErrors(code).map(_.message).toSet
    assertEquals(errors, expected.toSet, "Error did not contain expected value")
  }
}
