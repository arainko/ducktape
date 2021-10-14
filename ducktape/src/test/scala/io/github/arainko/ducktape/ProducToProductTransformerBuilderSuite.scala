package io.github.arainko.ducktape

import io.github.arainko.ducktape.*
import munit.*
import io.github.arainko.ducktape.internal.Derivation
import scala.deriving.Mirror

enum Enum1:
  case Sing1
  case Sing2
  case Sing3

enum Enum2:
  case Sing11
  case Sing31
  case Sing21

case class CoolCase1(int: String)
case class CoolCase2(int: String)

case class Product1(int: Int, str: String, list: List[Int], prod: CoolCase1)
case class Product3(int: Option[Int], str: String, list: List[Int], prod: Option[CoolCase2])

case class Product2(
  optInt: Option[Int],
  str: String,
  list: List[Int],
  additional: Double,
  computed: Map[String, String]
)

class MySuite extends FunSuite {

  // test("Product to Product should compile") {
  //   val prod1 = Product1(1, "prod1", List(1, 2, 3, 4))
  //   val transformed =
  //     TransformerBuilder
  //       .create[Product1, Product2]
  //       .withFieldComputed["computed"](from => Map(from.str -> from.str))
  //       .withFieldConst["additional"](5)
  //       .withFieldRenamed["int", "optInt"]
  //       .transformProdcutToProduct(prod1)

  //   println(transformed)
  // }

  val m1 = summon[Mirror.SumOf[Enum1]]
  val m2 = summon[Mirror.SumOf[Enum2]]

  val cos = Derivation.singletonToSingletonCase[
    "Sing21",
    Enum2.Sing21.type,
    Case.FromLabelsAndTypes[m1.MirroredElemLabels, m1.MirroredElemTypes]
  ]

  val trans = summon[Transformer[Product1, Product3]]

  test("trans test") {
    val prod1 = Product1(1, "prod1", List(1, 2, 3, 4), CoolCase1("COOL"))
    println(trans.transform(prod1))
    println(cos)
  }
}
