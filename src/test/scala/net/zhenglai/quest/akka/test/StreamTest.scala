package net.zhenglai.quest.akka.test

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.util.Failure

import akka.actor.ActorSystem
import akka.event.Logging
import akka.pattern
import akka.pattern.pipe
import akka.stream.scaladsl.{ Compression, Flow, Framing, Keep, Sink, Source }
import akka.stream.testkit.scaladsl.{ TestSink, TestSource }
import akka.stream.{ ActorMaterializer, Attributes, OverflowStrategy }
import akka.testkit.TestProbe
import akka.util.ByteString
import org.scalatest.FunSuite

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

  test("test with Sink.actorRef 2") {
    val sinkUnderTet = Flow[Int]
      .map(_.toString)
      .toMat(Sink.fold("")(_ + _))(Keep.right)

    val (ref, future) = Source.actorRef(8, OverflowStrategy.fail)
      .toMat(sinkUnderTet)(Keep.both)
      .run()

    ref ! 1
    ref ! 2
    ref ! 3
    ref ! akka.actor.Status.Success("done") // todo: complete the stream?
    ref ! 4 //

    val result = Await.result(future, 3 seconds)
    assert(result == "123")

    // Similarly to Sink.actorRef that provides control over
    // received elements, we can use Source.actorRef and have
    // full control over elements to be sent.
  }

  test("test with akka-stream-testkit") {
    // test sink
    val sourceUnderTest = Source(1 to 4)
      .filter(_ % 2 == 0)
      .map(_ * 2)

    sourceUnderTest
      .runWith(TestSink.probe[Int])
      .request(2)
      .expectNext(4, 8)
      .expectComplete()

    // test source
    val sinkUnderTest = Sink.cancelled
    TestSource.probe[Int]
      .toMat(sinkUnderTest)(Keep.left)
      .run()
      .expectCancellation()


    // test error conditions
    val sinkHeadUnderTest = Sink.head[Int]
    val (probe, future) = TestSource.probe[Int]
      .toMat(sinkHeadUnderTest)(Keep.both)
      .run()
    probe.sendError(new RuntimeException("boom"))
    Await.ready(future, 3 seconds)
    val Failure(exception) = future.value.get
    assert(exception.getMessage == "boom")
  }

  test("test flow with aka-stream-testkit") {
    val flowUnderTest = Flow[Int]
      .mapAsyncUnordered(2) { sleep =>
        pattern.after(10.millis * sleep, using = system.scheduler)(Future.successful(sleep))
      }

    val (pub, sub) = TestSource.probe[Int]
      .via(flowUnderTest)
      .toMat(TestSink.probe[Int])(Keep.both)
      .run()

    sub.request(n = 3)
    pub.sendNext(3)
    pub.sendNext(2)
    pub.sendNext(1)
    sub.expectNextUnordered(1, 2, 3)
    pub.sendError(new Exception("boom wow!"))
    val ex = sub.expectError()
    assert(ex.getMessage.contains("boom wow!"))
  }

  // For testing, it is possible to enable a special stream execution mode
  // that exercises concurrent execution paths more aggressively
  // (at the cost of reduced performance) and therefore helps exposing
  // race conditions in tests.
  // akka.stream.materializer.debug.fuzzing-mode = on

  test("logging elements") {
    val src = Source(1 to 4)
    val loggedSrc = src.map { elem => println(elem); elem }
    val loggedSrc2 = src.log("before-map")
      .withAttributes(Attributes.logLevels(onElement = Logging.WarningLevel))
      .map(identity)
    val sub = loggedSrc2.runWith(TestSink.probe[Int])
    sub.requestNext(1)
    sub.requestNext(2)
    sub.requestNext(3)
    sub.requestNext(4)
    sub.expectComplete()
  }

  test("limit or take") {
    val MAX_ALLOWED_SIZE = 10
    val src = Source(1 to 100)
    val limited: Future[Seq[Int]] =
      src.log("wow")
        .withAttributes(Attributes.logLevels(onElement = Logging.InfoLevel))
        .map { elem => println(elem); elem }
        .limit(MAX_ALLOWED_SIZE)
        .runWith(Sink.seq)
    println(Await.result(limited.failed, 1 second))
    // StreamLimitReachedException

    val ignoreOverflowProbe =
      src.take(MAX_ALLOWED_SIZE)
        .log("foo")
        .withAttributes(Attributes.logLevels(onElement = Logging.InfoLevel, onFailure = Logging.WarningLevel))
        .runWith(TestSink.probe[Int])
    ignoreOverflowProbe.request(100)
    ignoreOverflowProbe.expectNextN(1 to 10)
    ignoreOverflowProbe.expectComplete()
  }

  test("compression") {
    val uncompressed = Source(List("abc" * 12))
      .map(ByteString(_))
      .via(Compression.gzip)
      //      .map(_.utf8String)
      .log("gzipped")
      //      .map(ByteString(_))
      .via(Compression.gunzip())
      .map(_.utf8String)
      .log("gunzipped")
      .withAttributes(Attributes.logLevels(onElement = Logging.InfoLevel))
      .runWith(TestSink.probe)

    uncompressed
      .requestNext("abc" * 12)
  }

  test("framing") {
    val sub = Source(List("abc\nab", "c\r\nabcd\nabcde\r", "\nwow"))
      .log("raw")
      .map(ByteString(_))
      .via(Framing.delimiter(ByteString("\r\n"), maximumFrameLength = 100, allowTruncation = true))
      .map(_.utf8String)
      .log("splitted")
      .withAttributes(Attributes.logLevels(onElement = Logging.InfoLevel))
      .runWith(TestSink.probe)
    sub.request(100)
      .expectNextN(List("abc", "abc", "abcd", "abcde", "wow"))
  }
}
