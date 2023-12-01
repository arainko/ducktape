package io.github.arainko.ducktape

class LoggerSuite extends DucktapeSuite {
  test("log level should be Off") {
    val level = summon[internal.Logger.Level]
    assertEquals(level, internal.Logger.Level.Off, "Logger's log level should be set to Off to turn all log statements into ()")
  }
}
