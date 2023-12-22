package io.github.arainko.ducktape

enum Tree {


  case Root
  case Child(value: Int, parent: () => Tree, next: Option[Tree.Child])
}


object Playground  extends App {
  val cos: Tree.Child = 
    Tree.Child(
      1,
      () => Tree.Root,
      Some(
        Tree.Child(
          2,
          () => cos,
          None
        )
      )
    )

  println(cos.next.get.parent())
}
