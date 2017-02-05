package net.zhenglai.akka.quest.stream

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ BroadcastHub, Keep, MergeHub, RunnableGraph, Sink, Source }
import scala.concurrent.duration._

// Dynamic fan-in and fan-out with MergeHub and BroadcastHub
object HubMain extends App {
  implicit val system: ActorSystem = ActorSystem()
  implicit val mat: ActorMaterializer = ActorMaterializer()
  //  The Graph DSL does not allow to represent this,
  // all connections of the graph must be known in advance and must be connected upfront.
  // To allow dynamic fan-in and fan-out streaming, the Hubs should be used.
  // They provide means to construct Sink and Source pairs that are "attached" to each other,
  // but one of them can be materialized multiple times to implement dynamic fan-in or fan-out.


  // A MergeHub allows to implement a dynamic fan-in junction point in a graph where elements coming from different producers are emitted in a First-Comes-First-Served fashion.
  // If the consumer cannot keep up then all of the producers are backpressured.
  val consumer = Sink.foreach(println)

  // Attach a MergeHub Source to the consumer. This will materialize to a
  // corresponding Sink
  val runnableGraph: RunnableGraph[Sink[String, NotUsed]] =
  MergeHub.source[String](perProducerBufferSize = 16)
    .to(consumer)

  // By running/materializing the consumer we get back a Sink, and hence
  // now have access to feed elements into it. This Sink can be materialized
  // any number of times, and every element that enters the Sink will
  // be consumed by our consumer.
  val toConsumer: Sink[String, NotUsed] = runnableGraph.run()

  // Feeding two independent sources into the hub.
//  Source.single("hello").runWith(toConsumer)
//  Source.repeat("wow").runWith(toConsumer)
//  Source.single("hub").runWith(toConsumer)

  Thread.sleep(1)
  // ensures proper startup order.
  // Once we get the Sink, we can use it as many times as wanted.
  // Everything that is fed to it will be delivered to the consumer
  // we attached previously until it cancels.


  val producer = Source.tick(10 millis, 10 millis, "New message")
  // Attach a BroadcastHub Sink to the producer. This will materialize to a
  // corresponding Source.
  // (We need to use toMat and Keep.right since by default the materialized
  // value to the left is used)
  val runnableGraph2: RunnableGraph[Source[String, NotUsed]] =
  producer.toMat(BroadcastHub.sink(bufferSize = 256))(Keep.right)

  // By running/materializing the producer, we get back a Source, which
  // gives us access to the elements published by the producer.
  val fromProducer: Source[String, NotUsed] = runnableGraph2.run()


  // If there are no subscribers attached to this hub then it will not drop any elements
  // but instead backpressure the upstream producer until subscribers arrive.
  // 2 independent consumers
  fromProducer.runForeach(msg => println("consumer1: " + msg))
  fromProducer.runForeach(msg => println("consumer2: " + msg))

  Thread.sleep(1000)
  system.terminate()
}
