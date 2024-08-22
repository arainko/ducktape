package io.github.arainko.ducktape.issues

import io.github.arainko.ducktape.*

class Issue195Suite extends DucktapeSuite {
  test("missing Factory instances are reported in a nice way") {

    assertFailsToCompileContains {
      """
      import scala.collection.immutable.SortedSet
      import io.github.arainko.ducktape.to as convertTo
      import io.github.arainko.ducktape.Transformer

      final class MyClassA
      final class MyClassB

      given Transformer[MyClassA, MyClassB] = ???

      val x: SortedSet[MyClassA] = ???

      val y: SortedSet[MyClassB] = x.convertTo[SortedSet[MyClassB]]
      """
    }(
      // TODO: this is weird, the type in the explanation is different in CI ('java.util.Comparator[MyClassB]') as opposed to running it locally ('scala.math.Ordering.AsComparable[MyClassB]')
      """Couldn't derive a transformation between collections due to a missing instance of scala.Factory[MyClassB, scala.collection.immutable.SortedSet[MyClassB]].
Implicit search failure explanation: no implicit values were found that match type"""
    )

  }
}
