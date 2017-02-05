package net.zhenglai.akka.quest.stream

import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.stream.scaladsl.{ BroadcastHub, Flow, Keep, MergeHub, Sink, Source }
import akka.stream.{ ActorMaterializer, KillSwitches, UniqueKillSwitch }

object PubSubMain extends App {
  implicit val system: ActorSystem = ActorSystem()
  implicit val mat: ActorMaterializer = ActorMaterializer()

  // builds a Flow representing a publish-subscribe channel.
  // The input of the Flow is published to all subscribers
  // while the output streams all the elements published.


  // Obtain a Sink and Source which will publish and receive
  // from the "bus" respectively.
  val (sink, source) =
  MergeHub.source[String](perProducerBufferSize = 16)
    .toMat(BroadcastHub.sink(bufferSize = 256))(Keep.both)
    .run()

  // Ensure that the Broadcast output is dropped if there are no listening parties.
  // If this dropping Sink is not attached, then the broadcast hub will not drop any
  // elements itself when there are no subscribers, backpressuring the producer instead.
  source.runWith(Sink.ignore)


  sink.runWith(Source(List("a", "b")))

  source.runWith(Sink.foreach(println))


  // a publish-subscribe channel which can be used any number of times to attach new producers or consumers.
  val busFlow: Flow[String, String, UniqueKillSwitch] =
    Flow.fromSinkAndSource(sink, source)
      .joinMat(KillSwitches.singleBidi[String, String])(Keep.right)
      .backpressureTimeout(3 seconds)

  val switch: UniqueKillSwitch =
    Source.repeat("Hello world!")
      .viaMat(busFlow)(Keep.right)
      .to(Sink.foreach(println))
      .run()


  Thread.sleep(100)
  // shutdown externally
  switch.shutdown()

  system.terminate()
}
