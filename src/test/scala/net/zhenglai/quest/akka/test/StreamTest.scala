package net.zhenglai.quest.akka.test

import scala.concurrent.Await

import akka.stream.scaladsl.{ Flow, Keep, Sink, Source }
import org.scalatest.FunSuite
import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

// It is important to keep your data processing pipeline
// as separate sources, flows and sinks. This makes them easily testable
class StreamTest extends FunSuite {
  implicit val system: ActorSystem = ActorSystem()
  implicit val mat: ActorMaterializer = ActorMaterializer()

  test("test simple sink") {
    val sinkUnderTest = Flow[Int]
      .map(_ * 2)
      .toMat(Sink.fold(0)(_ + _))(Keep.right)

    val future = Source(1 to 4).runWith(sinkUnderTest)
    val result = Await.result(future, 3 seconds)
    assert(result == 20)
  }

  test("test simple source") {
    val arbitraryNum = 99
    val sourceUnderTest = Source.repeat(1).map(_ * 2)
    val future = sourceUnderTest.take(arbitraryNum).runWith(Sink.seq)
    val result = Await.result(future, 3 seconds)
    assert(result == Seq.fill(arbitraryNum)(2))
  }

  test("test simple flow") {
    val flowUnderTest = Flow[Int].takeWhile(_ < 5)

    val future = Source(1 to 10)
      .via(flowUnderTest)
      .runWith(Sink.seq)
    //      .runWith(Sink.fold(Seq.empty[Int])(_ :+ _))
    val result = Await.result(future, 3 seconds)
    assert(result == (1 to 4))
  }
}
