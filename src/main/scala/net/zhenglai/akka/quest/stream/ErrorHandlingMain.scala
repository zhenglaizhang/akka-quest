package net.zhenglai.akka.quest.stream

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.stream.{ ActorAttributes, ActorMaterializer, ActorMaterializerSettings, Supervision }
import akka.stream.scaladsl.{ Flow, Sink, Source }

// 3 ways to handle exceptions from application code
//Stop - The stream is completed with failure.
//Resume - The element is dropped and the stream continues.
//Restart - The element is dropped and the stream continues after restarting the
// stage. Restarting a stage means that any accumulated state is cleared. This
// is typically performed by creating a new instance of the stage.
object ErrorHandlingMain extends App {
  implicit val system: ActorSystem = ActorSystem()
  default()
  custom1()
  cust2()
  cust3()
  cust4()

  // By default the stopping strategy is used for all exceptions,
  // i.e. the stream will be completed with failure when an exception is thrown.
  private[this] def default() = {
    implicit val mat: ActorMaterializer = ActorMaterializer()
    val result = Source(0 to 5)
      .map(100 / _)
      .runWith(Sink.fold(0)(_ + _))

    println(Await.result(result.failed, 1 second))
  }

  private[this] def custom1() = {
    val decider: Supervision.Decider = {
      case _: ArithmeticException => Supervision.Resume
      case _ => Supervision.Stop
    }
    implicit val mat: ActorMaterializer = ActorMaterializer(
      ActorMaterializerSettings(system)
        .withSupervisionStrategy(decider)
    )

    // the element causing division by zero will be dropped
    // result here will be a Future completed with Success(228)
    val result = Source(0 to 5)
      .map(100 / _)
      .runWith(Sink.fold(0)(_ + _))

    println(Await.result(result, 1 second))
  }

  private[this] def cust2() = {
    implicit val mat: ActorMaterializer = ActorMaterializer()
    val decider: Supervision.Decider = {
      case _: ArithmeticException => Supervision.Resume
      case _ => Supervision.Stop
    }

    // The supervision strategy can also be defined for all operators of a flow
    val flow = Flow[Int]
      .filter(100 / _ < 50)
      .map(el => 100 / (5 - el))
      .withAttributes(ActorAttributes.supervisionStrategy(decider))
    val result = Source(0 to 5)
      .via(flow)
      .runWith(Sink.fold(0)(_ + _))
    println(Await.result(result, 1 second))
  }


  // Restart works in a similar way as Resume
  // with the addition that accumulated state,
  // if any, of the failing processing stage will be reset.
  private[this] def cust3() = {
    implicit val mat: ActorMaterializer = ActorMaterializer()
    val decider: Supervision.Decider = {
      case _: IllegalArgumentException => Supervision.Restart
      case _ => Supervision.Stop
    }

    val flow = Flow[Int]
      .scan(0) { (acc, elem) =>
        if (elem < 0) throw new IllegalArgumentException("negative not allowed")
        else acc + elem
      }
      .withAttributes(ActorAttributes.supervisionStrategy(decider))

    // the negative element cause the scan stage to be restarted,
    // i.e. start from 0 again
    // result here will be a Future completed with Success(Vector(0, 1, 4, 0, 5, 12))
    val result = Source(List(1, 3, -1, 5, 7))
      .via(flow)
      .limit(1000)
      .runWith(Sink.seq)
    println(Await.result(result, 1 second))
  }


  private[this] def cust4() = {
    implicit val mat: ActorMaterializer = ActorMaterializer()
    val decider: Supervision.Decider = {
      _ =>
        println("restarting...")
        Supervision.restart
    }

    val result =
      Source(List(0, 1, -1, 2, -1, 3, -1, 4, 5, 6))
        .map { x => println("before exc: " + x); x }
        .map { x => if (x < 0) throw new RuntimeException("boom!"); x }
        .map { x => println("after exc: " + x); x }
        .withAttributes(ActorAttributes.supervisionStrategy(decider))
        .runWith(Sink.seq)
    println(Await.result(result, 1 second))

  }


  // Stream supervision can also be applied to the futures of mapAsync.
  // If we would not use Resume the default stopping strategy would
  // complete the stream with failure on the first Future that was
  // completed with Failure.
  //   Supervision.resumingDecider

  Thread.sleep(100)
  system.terminate()
}


