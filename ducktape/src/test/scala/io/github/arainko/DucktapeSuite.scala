package io.github.arainko

import munit.FunSuite

trait DucktapeSuite extends FunSuite {
  transparent inline def assertFailsToCompile(inline code: String) = {
    assert(compiletime.testing.typeChecks(code), "Code snippet compiled despite expecting not to")
  }

  transparent inline def assertFailsToCompileWith(inline code: String)(expected: String) = {
    val errors = compiletime.testing.typeCheckErrors(code).map(_.message).mkString("\n")
    assertEquals(errors, expected)
  }
}
