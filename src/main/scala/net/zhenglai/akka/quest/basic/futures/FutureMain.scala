package net.zhenglai.akka.quest.basic.futures

import scala.concurrent.{ Await, Future, Promise }

import akka.actor.{ Actor, ActorSystem, Props }
import akka.util.Timeout
import akka.pattern.ask
import akka.pattern.pipe
import scala.concurrent.duration._

class A extends Actor {

  // If the nature of the Future calls invoked by the actor matches or is compatible with the activities of that actor (e.g. all CPU
  // bound and no latency requirements)
  // TODO: live template it!
  import context.dispatcher

  // places MessageDispatcher doubles as an ExecutionContext

  val f = Future("hello")
  //Future.successful & Future.failed doesn't need any ec

  override def receive = {
    case msg => sender() ! msg
  }
}

// A Future is a data structure used to retrieve the result of some concurrent operation.
// In order to execute callbacks and operations, Futures need something called an ExecutionContext,
object FutureMain extends App {

  implicit val system: ActorSystem = ActorSystem()
  implicit val ec = system.dispatcher

  // create an empty Promise,
  // to be filled later,
  // and obtain the corresponding Future
  val promise = Promise[String]()
  val p = promise.success("foo")
  p.future.foreach(println)


  val fr = Future
    .successful(4)
    .filter(_ % 2 == 1)
    .recover {
      // filter fails, it will have a java.util.NoSuchElementException
      case _: NoSuchElementException => 0
    }
  println(Await.result(fr, 2 seconds))

  // Unlike a Future that is returned from an Actor,
  // this Future is properly typed, and we also avoid
  // the overhead of managing an Actor.
  val f = Future {"hello" + " world"}

  val actor = system.actorOf(Props[A], name = "a")
  implicit val timeout = Timeout(3 seconds)
  val future = actor ? "wow"
  // returns Future[Any] since an Actor is dynamic
  val result = Await.result(future, timeout.duration).asInstanceOf[String]
  println(result)

  val futureSafer: Future[String] = (actor ? "boom").mapTo[String]
  println(Await.result(futureSafer, timeout.duration))

  // send the result of a Future to an actor with pipe constructor
  val again = ((actor ? "again") pipeTo actor).mapTo[String]
  println(Await.result(again, timeout.duration))


  val f1 = actor ? "msg1"
  val f2 = actor ? "msg2"
  val f3 = for {
    a <- f1.mapTo[String]
    b <- f2.mapTo[String]
    c <- (actor ? (a + b)).mapTo[Int]
  } yield c
  f3.foreach(println)

  Thread.sleep(1000)
  // TODO: more!!!
  system.terminate()
}
