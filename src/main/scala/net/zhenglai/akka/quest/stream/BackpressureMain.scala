package net.zhenglai.akka.quest.stream

import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, Fusing }
import akka.stream.scaladsl.{ Flow, Sink, Source }

// The back pressure protocol is defined in terms of the number of elements a downstream Subscriber is able to receive and buffer, referred to as `demand`
// Source => Publisher
// Sink   => Subscriber
// Flow   => Processor


// back-pressure mode:
//  "dynamic push / pull mode", since it will switch between push and pull based back-pressure models depending on the downstream being able to cope with the upstream production rate or not.


// Slow Publisher, fast Subscriber ( push-mode )
// The Reactive Streams protocol solves this by asynchronously signalling from the Subscriber to the Publisher Request(n:Int) signals. The protocol guarantees that the Publisher will never signal more elements than the signalled demand

// Fast Publisher, slow Subscriber ( pull-mode )

object BackpressureMain extends App {

  // Materialization is triggered at so called "terminal operations"
  // Materialization is currently performed synchronously on the materializing thread.
  // The actual stream processing is handled by actors started up during the streams materialization, which will be running on the thread pools they have been configured to run on

  // stream operator fusion support.
  // This means that the processing steps of a flow or stream graph can be executed within the same Actor and has three consequences:
  // 1) starting up a stream may take longer than before due to executing the fusion algorithm
  // 2) passing elements from one processing stage to the next is a lot faster between fused stages due to avoiding the asynchronous messaging overhead
  // 3) fused stream processing stages do no longer run in parallel to each other, meaning that only up to one CPU core is used for each fused part

  implicit val system: ActorSystem = ActorSystem()
  implicit val mat: ActorMaterializer = ActorMaterializer()
  implicit val ec = system.dispatcher
  val flow = Flow[Int].map(_ * 2).filter(_ > 500)
  // pre-fusing and then reusing a stream blueprint
  // Fusing itself is a time-consuming operation, so we might cache the fused result ahead
  val fused = Fusing.aggressive(flow)
  Source.fromIterator(() => Iterator from 0)
    .via(fused)
    .take(1000)
    .runFold(0)(_ + _)
    .foreach(println)

  // we create two regions within the flow which will be executed in one Actor each
  Source(List(1, 2, 3))
    .map(_ + 1).async // assuming + is extremely costly operation, executed in one actor in one cpu
    .map(_ * 2) // executed in another actor (cpu)
    .to(Sink.ignore)
    .run()


  // TODO: async? fusing?
  // Without fusing (i.e. up to version 2.0-M2) each stream processing stage had an implicit input buffer that holds a few elements for efficiency reasons.
  // If your flow graphs contain cycles then these buffers may have been crucial in order to avoid deadlocks.
  // With fusing these implicit buffers are no longer there, data elements are passed without buffering between fused stages.
  // In those cases where buffering is needed in order to allow the stream to run at all, you will have to insert explicit buffers with the .buffer() combinator—typically a buffer of size 2 is enough to allow a feedback loop to function.

  Thread.sleep(1000)
  system.terminate()
}
