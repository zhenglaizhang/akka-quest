
// hard-coding it, using method-parameters (explicit or implicit), constructor injection, abstract-member injection, global mutable state injection, setter injection

//First, hard-code the dependency
def sayHello(msg: String) = println("Hello World: " + msg)

sayHello("Mooo")


// Second, pass it as a parameter to the method which needs it
def sayHello2(msg: String, log: (String) => Unit) = log("Hello World: " + msg)

sayHello2("Mooo", System.out.println)
sayHello2("ErrorMooo", System.err.println)

// or some remote-logger, test-logger...


// Third, inject it into multiple methods by passing it into containing class
// Constructor Injection
// if the same logger is passed to lots if different functions
class Container(log: String => Unit) {
  def func() = func2()

  def func2() = func3()

  def func3() = sayHello("Moo")

  def sayHello(msg: String) = log("Hello World: " + msg)
}

new Container(System.out.println).func()
new Container(System.err.println).func()

// The same technique applies if you need the same logger injected into different classes or objects:
// simply nest those classes or objects inside (in this case) the Container class and they'll all
// get access to it without needing to add log into each-and-every method signature.


// Fourth, if your containing class is split into traits in multiple files, use abstract members
// If you're trying to use Constructor Injection but your Container class is getting too large, split it into separate files as individual traits.

// Foo.scala
trait Foo {
  def log: String => Unit

  def func() = func2()

  def func2() = ???
}

// Bar.scala
trait Bar extends Foo {
  def log: String => Unit

  def func3() = sayHello("Moo")

  def sayHello(msg: String) = log("Hello World: " + msg)
}

class Container2(val log: String => Unit) extends Foo with Bar

new Container2(System.out.println).func3()
new Container2(System.err.println).func3()
// you will need to pass log into each individual trait.
// traits can't take constructor parameters,
// but you can do it by giving them an abstract method log
// and having the main Container class extend
// all these traits and implement log concretely.


// Fifth, make the method parameter implicit
// If you find yourself passing the same logger everywhere,
//And the use-sites are "all over the place", not limited to one (or a small number of) classes, then make it an implicit parameter

def sayHello4(msg: String)(implicit log: String => Unit) = log("Hello World with Implicit: " + msg)

implicit val stdLogger: String => Unit = System.out.println
sayHello4("wow")

// In general, you should only do this if you are passing it around a lot: into dozens of callsites at least. Nevertheless, in a large program that's not unreasonable, especially for common things like logging.
// However, if the use sites are all relatively localized, you should prefer to use Constructor Injection rather than creating a new implicit parameter just for one small section of your code. Reserve implicit parameters for the cases where the callsites are scattered and constructor injection into all the disparate classes becomes tedious.


// If all else fails, use "Dynamic Variables' aka global-mutable-state
// var log: String => Unit = null

// Don't use setter injection
// "Setter Injection" refers to instantiating an object, and then setting some variable onto that object which it will use when you call methods on it.

//As described in the section on Immutability & Mutability, don't do that: mutable state should only be used to model mutable things, and not as an ad-hoc way of passing parameters to a function call.


// if you know there is multiple things that can go wrong, use a Simple Sealed Trait

sealed trait Result

object Result {

  case class Success(value: String) extends Result

  case class Failure(msg: String) extends Result

  case class Error(msg: String) extends Result

}

def functionMayFail: Result = Result.Success("success")

functionMayFail match {
  case Result.Success(s) => println(s)
  case Result.Failure(s) => println(s)
  case Result.Error(s) => println(s)
}

