package net.zhenglai.akka.quest.stream

import java.nio.file.Paths

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }

import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.scaladsl.{ FileIO, Flow, Keep, Sink, Source }
import akka.stream.{ ActorMaterializer, IOResult, ThrottleMode }
import akka.util.ByteString
import akka.{ Done, NotUsed }

// Akka Streams does not send dropped stream elements to the dead letter office
// The process of materialization will often create specific objects that are useful to interact with the processing engine once it is running, for example for shutting it down or for extracting metrics.
// This means that the materialization function produces a result termed the materialized value of a graph.

/*
Source: something with exactly one output stream
Sink: something with exactly one input stream
Flow: something with exactly one input and one output stream
BidiFlow: something with exactly two input streams and two output streams that conceptually behave like two Flows of opposite direction
Graph: a packaged stream processing topology that exposes a certain set of input and output ports, characterized by an object of type Shape.

Source
A processing stage with exactly one output, emitting data elements whenever downstream processing stages are ready to receive them.
Sink
A processing stage with exactly one input, requesting and accepting data elements possibly slowing down the upstream producer of elements
Flow
A processing stage which has exactly one input and output, which connects its up- and downstreams by transforming the data elements flowing through it.
RunnableGraph
A Flow that has both ends "attached" to a Source and Sink respectively, and is ready to be run().
* */


// After a stream is properly terminated by having both a source and a sink, it will be represented by the RunnableGraph type, indicating that it is ready to be executed.

// When we talk about asynchronous, non-blocking backpressure we mean that the processing stages available in Akka Streams will not use blocking calls but asynchronous message passing to exchange messages between each other, and they will use asynchronous means to slow down a fast producer, without blocking its thread.
// This is a thread-pool friendly design, since entities that need to wait (a fast producer waiting on a slow consumer) will not block the thread but can hand it back for further use to an underlying thread-pool.


// This property of bounded buffers is one of the differences from the actor model, where each actor usually has an unbounded, or a bounded, but dropping mailbox.
// Akka Stream processing entities have bounded "mailboxes" that do not drop.

// Back-pressure:
// A means of flow-control, a way for consumers of data to notify a producer about their current availability,
// effectively slowing down the upstream producer to match their consumption speeds.
// In the context of Akka Streams back-pressure is always understood as non-blocking and asynchronous.

// ERROR vs. FAILURE
// an error is accessible within the stream as a normal data element, while a failure means that the stream itself has failed and is collapsing.
// In concrete terms, on the Reactive Streams interface level data elements (including errors) are signaled via onNext while failures raise the onError signal.
// the Reactive Streams interfaces (Publisher/Subscription/Subscriber) are modeling the low-level infrastructure for passing streams between execution units

// Streams always start flowing from a Source[Out,M1] then can continue through Flow[In,Out,M2] elements or more advanced graph elements to finally be consumed by a Sink[In,M3]
object SimpleStreamMain extends App {
  // NotUsed: auxiliary value
  implicit val system: ActorSystem = ActorSystem("SimpleStreamSystem")
  implicit val mat: ActorMaterializer = ActorMaterializer()
  implicit val ec = system.dispatcher
  val log = Logging(system, getClass)

  val source: Source[Int, NotUsed] = Source(1 to 100)

  // The Materializer is a factory for stream execution engines, it is the thing that makes streams run

  // the Source is just a description of what you want to run, and like an architect’s blueprint it can be reused, incorporated into a larger design.
  //  source.runFold(0)(_ + _).foreach(i => print(s"sum: $i"))
  //  source.runForeach(i => log.info(i.toString))

  //cause eventual buffer overflows and instability of such systems.
  // Instead Akka Streams depend on internal backpressure signals that allow to control what should happen in such scenarios.

  def lineSink(fileName: String): Sink[String, Future[IOResult]] =
    Flow[String] // left: NotUsed
      .map(s => ByteString(s + "\n"))
      .toMat(FileIO.toPath(Paths.get(fileName)))(Keep.right)

  val factorials = source.scan(BigInt(1))((acc, next) => acc * next)
  val result: Future[IOResult] =
    factorials
      .map(num => ByteString(s"$num\n"))
      .runWith(FileIO.toPath(Paths.get("target/factorials.txt")))
  result.foreach(r => log.info("io result: {}", r))

  factorials
    .map(_.toString)
    .runWith(lineSink("target/factorials2.txt"))

  // Akka Streams implicitly implement pervasive flow control, all combinators respect back-pressure.
  // This allows the throttle combinator to signal to all its upstream sources of data that it can only accept elements at a certain rate
  // —when the incoming rate is higher than one per second the throttle combinator will assert back-pressure upstream.
  val done: Future[Done] =
    factorials
      //      .zipWith(Source(0 to 100))((num, idx) => s"$idx! = $num")
      .zipWithIndex
      .map(s => s"${s._2}! = ${s._1}")
      .throttle(1, 100 milliseconds, 1, ThrottleMode.shaping)
      .runForeach(println)


  Await.ready(result, 10.seconds)
  print(Await.result(done, 100.seconds))
  mat.shutdown()
  system.terminate()
}
