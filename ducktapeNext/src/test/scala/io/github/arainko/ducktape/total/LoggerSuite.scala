package io.github.arainko.ducktape

import io.github.arainko.ducktape.DucktapeSuite
import io.github.arainko.ducktape.internal.*

class LoggerSuite extends DucktapeSuite {
  test("log level should be Off") {
    val result = compiletime.testing.typeChecks("""val level: Logger.Level.Off.type = summon[Logger.Level]""")
    assertEquals(result, true, "Logger's log level should be set to Off to turn all log statements into ()")
  }
}
