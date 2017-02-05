package net.zhenglai.quest.akka.test

import scala.concurrent.Await

import akka.stream.scaladsl.{ Flow, Keep, Sink, Source }
import org.scalatest.FunSuite
import scala.concurrent.duration._

import akka.pattern.pipe
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.testkit.TestProbe

// It is important to keep your data processing pipeline
// as separate sources, flows and sinks. This makes them easily testable
class StreamTest extends FunSuite {
  implicit val system: ActorSystem = ActorSystem()
  implicit val mat: ActorMaterializer = ActorMaterializer()
  implicit val ec = system.dispatcher

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

  // Akka Stream offers integration with Actors out of the box.
  test("test with testkit") {
    val sourceUnderTest = Source(1 to 4).grouped(2)
    val probe = TestProbe()
    sourceUnderTest.runWith(Sink.seq).pipeTo(probe.ref)
    probe.expectMsg(3 seconds, Seq(Seq(1, 2), Seq(3, 4)))
  }

  test("test with Sink.actorRef") {
    case object Tick
    val sourceUnderTest = Source.tick(0.seconds, 200.millis, Tick)
    val probe = TestProbe()
    val cancellable = sourceUnderTest
      .to(Sink.actorRef(probe.ref, "completed"))
      .run()
    probe.expectMsg(1 second, Tick)
    probe.expectNoMsg(200 millis)
    probe.expectMsg(3.seconds, Tick)
    cancellable.cancel()
    probe.expectMsg(3 seconds, "completed")
  }
}
