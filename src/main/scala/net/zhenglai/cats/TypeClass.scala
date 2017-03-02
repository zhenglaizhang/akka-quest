package net.zhenglai.cats


trait Monoid[A] {
  def empty: A
  def combine(x: A, y: A): A
}

object TypeClass extends App {

  val intAdditionMonoid: Monoid[Int] = new Monoid[Int] {
    def empty = 0
    def combine(x: Int, y: Int) = x + y
  }

  // type class
  def combineAll[A](list: List[A], A: Monoid[A]): A = list.foldRight(A.empty)(A.combine)

  // subtyping
  // what if list is empty??? boom!!
  def combineAll2[A <: Monoid[A]](list: List[A]): A = ???
}
