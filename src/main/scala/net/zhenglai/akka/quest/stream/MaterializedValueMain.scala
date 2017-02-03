package net.zhenglai.akka.quest.stream

import scala.concurrent.{ Future, Promise }

import akka.actor.{ ActorSystem, Cancellable }
import akka.stream.{ ActorMaterializer, ClosedShape }
import akka.stream.scaladsl.{ Flow, GraphDSL, Keep, RunnableGraph, Sink, Source }

object MaterializedValueMain extends App {
  implicit val system: ActorSystem = ActorSystem()
  implicit val mat: ActorMaterializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  // every processing stage in Akka Streams can provide a materialized value after being materialized

  // An source that can be signalled explicitly from the outside
  val source: Source[Int, Promise[Option[Int]]] = Source.maybe[Int]

  // A flow that internally throttles elements to 1/second, and returns a Cancellable
  // which can be used to shut down the stream
  // TODO fix it
  val flow: Flow[Int, Int, Cancellable] = ???

  val sink: Sink[Int, Future[Int]] = Sink.head[Int]


  // by default, the materialized value of the left most stage is preserved
  val r1: RunnableGraph[Promise[Option[Int]]] = source.via(flow).to(sink)

  val r2: RunnableGraph[Cancellable] = source.viaMat(flow)(Keep.right).to(sink)
  val r3: RunnableGraph[Future[Int]] = source.via(flow).toMat(sink)(Keep.right)


  val r4: Future[Int] = source.via(flow).runWith(sink)
  val r5: Promise[Option[Int]] = flow.to(sink).runWith(source)
  val r6: (Promise[Option[Int]], Future[Int]) = flow.runWith(source, sink)

  val r7: RunnableGraph[(Promise[Option[Int]], Cancellable)] =
    source.viaMat(flow)(Keep.both).to(sink)

  val r8: RunnableGraph[((Promise[Option[Int]], Cancellable), Future[Int])] =
    source.viaMat(flow)(Keep.both).toMat(sink)(Keep.both)

  val r11: RunnableGraph[(Promise[Option[Int]], Cancellable, Future[Int])] =
    r8.mapMaterializedValue { case ((p, c), f) => (p, c, f) }

  val (promise, cancellable, future) = r11.run()

  promise.success(None)
  cancellable.cancel()
  future.map(_ + 3)

  // In Akka Streams almost all computation stages preserve input order of elements.
  // This property is even uphold by async operations such as mapAsync,
  // however an unordered version exists called mapAsyncUnordered which does not preserve this ordering.
  // However, in the case of Junctions which handle multiple input streams (e.g. Merge) the output order is, in general, not defined for elements arriving on different input ports.
  // Specialized elements such as Zip however do guarantee their outputs order, as each output element depends on all upstream elements having been signalled already
  // If you find yourself in need of fine grained control over order of emitted elements in fan-in scenarios consider using MergePreferred or GraphStage – which gives you full control over how the merge is performed.

  // with Graph API
  // todo: understand it
  val r12: RunnableGraph[(Promise[Option[Int]], Cancellable, Future[Int])] =
    RunnableGraph.fromGraph(GraphDSL.create(source, flow, sink)((_, _, _)) { implicit builder =>
      (src, f, dst) =>
        import GraphDSL.Implicits._
        src ~> f ~> dst
        ClosedShape
    })


  Thread.sleep(1000)
  system.terminate()
}
